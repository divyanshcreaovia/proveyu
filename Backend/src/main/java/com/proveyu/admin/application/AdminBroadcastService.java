package com.proveyu.admin.application;

import com.proveyu.admin.domain.AdminModel.Audience;
import com.proveyu.admin.domain.AdminModel.BroadcastRequest;
import com.proveyu.admin.domain.AdminModel.BroadcastResult;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.UUID;

/**
 * A message from the ProveYu team to a group of users: an in-app notification (the bell) and, if asked, an e-mail. Blocked users are
 * skipped. E-mails are sent in the background.
 */
@Service
@RequiredArgsConstructor
public class AdminBroadcastService {

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final EmailService email;
    private final AdminAuditService audit;

    private record Recipient(UUID id, String email) {}

    public BroadcastResult send(UUID adminId, BroadcastRequest r) {
        List<Recipient> to = recipients(r.audience(), r.collegeId());
        String text = r.title().trim() + ": " + r.message().trim();
        int mails = 0;
        for (Recipient x : to) {
            notifications.notify(x.id(), "ANNOUNCEMENT", text);
            if (r.sendEmail() && x.email() != null) {
                email.sendHtml(x.email(), r.title().trim(), "<p>" + HtmlUtils.htmlEscape(r.message().trim()).replace("\n", "<br>")
                        + "</p><p style=\"color:#64748b\">ProveYu team</p>");
                mails++;
            }
        }
        audit.log(adminId, "BROADCAST_SENT", "AUDIENCE", r.audience() == Audience.COLLEGE ? String.valueOf(r.collegeId()) : r.audience().name(),
                "Sent \"" + r.title().trim() + "\" to " + to.size() + " user(s)" + (r.sendEmail() ? " with e-mail" : "") + " (" + r.audience().name().toLowerCase() + ")");
        return new BroadcastResult(to.size(), mails);
    }

    /** How many users a broadcast would reach, for the confirmation step. */
    public int count(Audience audience, UUID collegeId) {
        return recipients(audience, collegeId).size();
    }

    private List<Recipient> recipients(Audience a, UUID collegeId) {
        String sql = switch (a) {
            case ALL -> "select id, email from users where is_active = 1 and role <> 'ADMIN'";
            case STUDENTS -> "select id, email from users where is_active = 1 and role = 'CANDIDATE'";
            case COLLEGE_STAFF -> "select id, email from users where is_active = 1 and role in ('COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER')";
            case COLLEGE -> """
                    select u.id, u.email from users u where u.is_active = 1 and u.id in (
                      select s.user_id from college_students s where s.college_id = ? and s.deleted_at is null and s.user_id is not null
                      union select m.user_id from college_members m where m.college_id = ? and m.status = 'ACTIVE')""";
        };
        if (a == Audience.COLLEGE) {
            if (collegeId == null) throw new DomainException("Choose the college", HttpStatus.BAD_REQUEST, "COLLEGE_REQUIRED");
            return jdbc.query(sql, (rs, i) -> new Recipient(UUID.fromString(rs.getString(1)), rs.getString(2)), collegeId.toString(), collegeId.toString());
        }
        return jdbc.query(sql, (rs, i) -> new Recipient(UUID.fromString(rs.getString(1)), rs.getString(2)));
    }
}
