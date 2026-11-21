package com.proveyu.enquiry.application;

import com.proveyu.admin.application.AdminAuditService;
import com.proveyu.enquiry.domain.EnquiryModel.Enquiry;
import com.proveyu.enquiry.domain.EnquiryModel.EnquiryPage;
import com.proveyu.enquiry.domain.EnquiryModel.NewEnquiry;
import com.proveyu.enquiry.domain.EnquiryModel.OrgType;
import com.proveyu.enquiry.domain.EnquiryModel.Status;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Pricing page form. Anyone can send it (no sign-in); every admin gets a bell notification and the team inbox an e-mail when
 * support.inbox-email is set. Admins see the list in Admin > Pricing enquiries and mark each one contacted or closed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnquiryService {

    /** One e-mail address can send this many enquiries a day, so the list cannot be flooded. */
    static final int DAILY_LIMIT_PER_EMAIL = 3;
    private static final String COLS = "id, full_name, email, phone, organization, org_type, designation, city, student_count, message, status, "
            + "admin_note, handled_by, created_at, updated_at";

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final EmailService email;
    private final AdminAuditService audit;

    @Value("${support.inbox-email:}")
    private String inboxEmail;

    private final RowMapper<Enquiry> mapper = (rs, i) -> new Enquiry(UUID.fromString(rs.getString("id")), rs.getString("full_name"),
            rs.getString("email"), rs.getString("phone"), rs.getString("organization"), OrgType.valueOf(rs.getString("org_type")),
            rs.getString("designation"), rs.getString("city"), rs.getString("student_count"), rs.getString("message"),
            Status.valueOf(rs.getString("status")), rs.getString("admin_note"), rs.getString("handled_by"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    // ------------------------------------------------------------------ public form

    @Transactional
    public void submit(NewEnquiry r) {
        if (r.website() != null && !r.website().isBlank()) {
            log.info("[PRICING ENQUIRY] dropped a bot submission");
            return; // answer like a success so the bot learns nothing
        }
        String mail = r.email().trim().toLowerCase(Locale.ROOT);
        Integer today = jdbc.queryForObject("select count(*) from pricing_enquiries where email = ? and created_at > now() - interval 1 day",
                Integer.class, mail);
        if (today != null && today >= DAILY_LIMIT_PER_EMAIL) {
            throw new DomainException("We already have your request. Our team will contact you soon.", HttpStatus.TOO_MANY_REQUESTS, "ENQUIRY_LIMIT");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into pricing_enquiries (id, full_name, email, phone, organization, org_type, designation, city, student_count, message)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id.toString(), r.fullName().trim(), mail, r.phone().trim(), r.organization().trim(),
                r.orgType().name(), blankToNull(r.designation()), blankToNull(r.city()), blankToNull(r.studentCount()), blankToNull(r.message()));

        String who = r.fullName().trim() + " (" + r.organization().trim() + ")";
        for (String admin : jdbc.queryForList("select id from users where role = 'ADMIN' and is_active = 1", String.class)) {
            notifications.notify(UUID.fromString(admin), "ENQUIRY", "New pricing enquiry from " + who + ". Open Pricing enquiries to call them back.");
        }
        if (inboxEmail != null && !inboxEmail.isBlank()) {
            email.sendHtml(inboxEmail.trim(), "[Pricing enquiry] " + r.organization().trim(),
                    "<p><strong>" + esc(r.fullName()) + "</strong>" + (r.designation() == null || r.designation().isBlank() ? "" : ", " + esc(r.designation()))
                            + "<br>" + esc(r.organization()) + " · " + r.orgType().name().toLowerCase(Locale.ROOT)
                            + (r.city() == null || r.city().isBlank() ? "" : " · " + esc(r.city())) + "</p>"
                            + "<p>E-mail: " + esc(mail) + "<br>Phone: " + esc(r.phone()) + "<br>Students: " + esc(studentLabel(r.studentCount())) + "</p>"
                            + (r.message() == null || r.message().isBlank() ? "" : "<p>" + esc(r.message().trim()).replace("\n", "<br>") + "</p>")
                            + "<p style=\"color:#64748b\">Mark it contacted in the ProveYu admin portal, Pricing enquiries.</p>");
        }
    }

    // ------------------------------------------------------------------ admin

    public EnquiryPage list(Status status, String q, int page, int size) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(0, page);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null) { where.append(" and status = ?"); args.add(status.name()); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            where.append(" and (lower(full_name) like ? or lower(email) like ? or lower(organization) like ? or phone like ? or lower(coalesce(city, '')) like ?)");
            for (int i = 0; i < 5; i++) args.add(like);
        }
        Long total = jdbc.queryForObject("select count(*) from pricing_enquiries" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add(page * size);
        List<Enquiry> rows = jdbc.query("select " + COLS + " from pricing_enquiries" + where
                + " order by field(status, 'NEW', 'CONTACTED', 'CLOSED'), created_at desc limit ? offset ?", mapper, pageArgs.toArray());
        return new EnquiryPage(rows, total == null ? 0 : total, page, size);
    }

    public long newCount() {
        Long n = jdbc.queryForObject("select count(*) from pricing_enquiries where status = 'NEW'", Long.class);
        return n == null ? 0 : n;
    }

    @Transactional
    public Enquiry setStatus(UUID adminId, UUID id, Status status, String note) {
        Enquiry e = get(id);
        String adminName = jdbc.queryForObject("select full_name from users where id = ?", String.class, adminId.toString());
        jdbc.update("update pricing_enquiries set status = ?, admin_note = ?, handled_by = ? where id = ?",
                status.name(), note == null ? e.adminNote() : blankToNull(note), adminName, id.toString());
        audit.log(adminId, "ENQUIRY_" + status.name(), "PRICING_ENQUIRY", id.toString(),
                "Marked the pricing enquiry from " + e.fullName() + " (" + e.organization() + ") as " + status.name().toLowerCase(Locale.ROOT));
        return get(id);
    }

    // ------------------------------------------------------------------ helpers

    private Enquiry get(UUID id) {
        return jdbc.query("select " + COLS + " from pricing_enquiries where id = ?", mapper, id.toString()).stream().findFirst()
                .orElseThrow(() -> new DomainException("Enquiry not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    static String studentLabel(String v) {
        if (v == null || v.isBlank()) return "not given";
        return switch (v) {
            case "UNDER_500" -> "under 500";
            case "500_1000" -> "500 - 1,000";
            case "1000_3000" -> "1,000 - 3,000";
            case "3000_PLUS" -> "3,000+";
            default -> v;
        };
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String esc(String s) { return s == null ? "" : HtmlUtils.htmlEscape(s.trim()); }

    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }
}
