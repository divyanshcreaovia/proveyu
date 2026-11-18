package com.proveyu.support.application;

import com.proveyu.admin.application.AdminAuditService;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import com.proveyu.support.domain.SupportModel.Category;
import com.proveyu.support.domain.SupportModel.NewTicket;
import com.proveyu.support.domain.SupportModel.Reply;
import com.proveyu.support.domain.SupportModel.Status;
import com.proveyu.support.domain.SupportModel.Ticket;
import com.proveyu.support.domain.SupportModel.TicketPage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Help & support. A student or college staff member writes to the ProveYu team from the portal; every admin is notified in the bell
 * (and the team inbox gets an e-mail when support.inbox-email is set). The admin's answer is shown in the user's portal and sent as a
 * notification, and by e-mail if the admin ticks it.
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    /** A user can open this many requests per day, so the inbox cannot be flooded. */
    static final int DAILY_LIMIT = 10;
    private static final String COLS = "id, user_name, user_email, user_role, portal, college_id, college_name, category, subject, message, status, "
            + "reply, replied_by, replied_at, created_at, updated_at";

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final EmailService email;
    private final AdminAuditService audit;

    @Value("${support.inbox-email:}")
    private String inboxEmail;

    private final RowMapper<Ticket> mapper = (rs, i) -> new Ticket(UUID.fromString(rs.getString("id")), rs.getString("user_name"),
            rs.getString("user_email"), rs.getString("user_role"), rs.getString("portal"),
            rs.getString("college_id") == null ? null : UUID.fromString(rs.getString("college_id")), rs.getString("college_name"),
            Category.valueOf(rs.getString("category")), rs.getString("subject"), rs.getString("message"), Status.valueOf(rs.getString("status")),
            rs.getString("reply"), rs.getString("replied_by"), instant(rs.getTimestamp("replied_at")), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    // ------------------------------------------------------------------ user side

    @Transactional
    public Ticket create(UUID userId, NewTicket r) {
        Map<String, Object> u = jdbc.queryForMap("select full_name, email, role from users where id = ?", userId.toString());
        String role = (String) u.get("role");
        String portal = switch (role) {
            case "CANDIDATE" -> "STUDENT";
            case "COLLEGE_ADMIN", "COLLEGE_COORDINATOR", "COLLEGE_VIEWER" -> "COLLEGE";
            default -> throw new DomainException("Help & support is for the college and student portals", HttpStatus.FORBIDDEN, "SUPPORT_NOT_AVAILABLE");
        };
        Integer today = jdbc.queryForObject("select count(*) from support_tickets where user_id = ? and created_at > now() - interval 1 day",
                Integer.class, userId.toString());
        if (today != null && today >= DAILY_LIMIT) {
            throw new DomainException("You have sent " + DAILY_LIMIT + " requests today. Please wait for the team to answer.", HttpStatus.TOO_MANY_REQUESTS,
                    "SUPPORT_LIMIT");
        }
        String[] college = college(userId, portal);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into support_tickets (id, user_id, user_name, user_email, user_role, portal, college_id, college_name, category, subject, message)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id.toString(), userId.toString(), u.get("full_name"), u.get("email"), role, portal,
                college[0], college[1], r.category().name(), r.subject().trim(), r.message().trim());

        String who = u.get("full_name") + (college[1] != null ? " (" + college[1] + ")" : "");
        for (String admin : jdbc.queryForList("select id from users where role = 'ADMIN' and is_active = 1", String.class)) {
            notifications.notify(UUID.fromString(admin), "SUPPORT", "New support request from " + who + ": " + r.subject().trim());
        }
        if (inboxEmail != null && !inboxEmail.isBlank()) {
            email.sendHtml(inboxEmail.trim(), "[Support] " + r.subject().trim(), "<p><strong>" + esc(who) + "</strong> &lt;" + esc((String) u.get("email"))
                    + "&gt; · " + portal.toLowerCase() + " portal · " + r.category().name().replace('_', ' ').toLowerCase() + "</p><p>"
                    + esc(r.message().trim()).replace("\n", "<br>") + "</p><p style=\"color:#64748b\">Answer it in the ProveYu admin portal, Support.</p>");
        }
        return get(id);
    }

    public List<Ticket> mine(UUID userId) {
        return jdbc.query("select " + COLS + " from support_tickets where user_id = ? order by created_at desc limit 50", mapper, userId.toString());
    }

    // ------------------------------------------------------------------ admin side

    public TicketPage list(Status status, String q, int page, int size) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(0, page);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null) { where.append(" and status = ?"); args.add(status.name()); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            where.append(" and (lower(user_name) like ? or lower(user_email) like ? or lower(subject) like ? or lower(coalesce(college_name, '')) like ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        Long total = jdbc.queryForObject("select count(*) from support_tickets" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add(page * size);
        List<Ticket> rows = jdbc.query("select " + COLS + " from support_tickets" + where
                + " order by field(status, 'OPEN', 'ANSWERED', 'CLOSED'), created_at desc limit ? offset ?", mapper, pageArgs.toArray());
        return new TicketPage(rows, total == null ? 0 : total, page, size);
    }

    public long openCount() {
        Long n = jdbc.queryForObject("select count(*) from support_tickets where status = 'OPEN'", Long.class);
        return n == null ? 0 : n;
    }

    @Transactional
    public Ticket reply(UUID adminId, UUID id, Reply r) {
        Ticket t = get(id);
        String adminName = jdbc.queryForObject("select full_name from users where id = ?", String.class, adminId.toString());
        Status next = r.close() ? Status.CLOSED : Status.ANSWERED;
        jdbc.update("update support_tickets set reply = ?, replied_by = ?, replied_at = now(), status = ? where id = ?",
                r.reply().trim(), adminName, next.name(), id.toString());
        UUID userId = UUID.fromString(jdbc.queryForObject("select user_id from support_tickets where id = ?", String.class, id.toString()));
        notifications.notify(userId, "SUPPORT", "The ProveYu team answered \"" + t.subject() + "\". Open Help & support to read it.");
        if (r.sendEmail()) {
            email.sendHtml(t.userEmail(), "Re: " + t.subject(), "<p>Hi " + esc(t.userName()) + ",</p><p>" + esc(r.reply().trim()).replace("\n", "<br>")
                    + "</p><hr><p style=\"color:#64748b\">Your message: " + esc(t.message()).replace("\n", "<br>") + "</p><p style=\"color:#64748b\">ProveYu team</p>");
        }
        audit.log(adminId, "SUPPORT_ANSWERED", "SUPPORT_TICKET", id.toString(), "Answered \"" + t.subject() + "\" from " + t.userName()
                + (r.close() ? " and closed it" : "") + (r.sendEmail() ? " (with e-mail)" : ""));
        return get(id);
    }

    @Transactional
    public Ticket setStatus(UUID adminId, UUID id, Status status) {
        Ticket t = get(id);
        jdbc.update("update support_tickets set status = ? where id = ?", status.name(), id.toString());
        audit.log(adminId, "SUPPORT_" + status.name(), "SUPPORT_TICKET", id.toString(), "Marked \"" + t.subject() + "\" from " + t.userName() + " as "
                + status.name().toLowerCase());
        return get(id);
    }

    // ------------------------------------------------------------------ helpers

    private Ticket get(UUID id) {
        return jdbc.query("select " + COLS + " from support_tickets where id = ?", mapper, id.toString()).stream().findFirst()
                .orElseThrow(() -> new DomainException("Support request not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /** {collegeId, collegeName} of the user's college, or nulls. */
    private String[] college(UUID userId, String portal) {
        String sql = "STUDENT".equals(portal)
                ? "select c.college_id, c.college_name from college_students s join college_profiles c on c.college_id = s.college_id where s.user_id = ? and s.deleted_at is null limit 1"
                : "select c.college_id, c.college_name from college_members m join college_profiles c on c.college_id = m.college_id where m.user_id = ? and m.status = 'ACTIVE' limit 1";
        return jdbc.query(sql, (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, userId.toString()).stream().findFirst().orElse(new String[]{null, null});
    }

    private static String esc(String s) { return s == null ? "" : HtmlUtils.htmlEscape(s); }

    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }
}
