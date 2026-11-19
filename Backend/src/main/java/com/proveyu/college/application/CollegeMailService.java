package com.proveyu.college.application;

import com.proveyu.college.domain.EmailTemplate;
import com.proveyu.college.infrastructure.EmailTemplateRepository;
import com.proveyu.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders college e-mails from per-college templates (college_email_templates) falling back to defaults.
 * Placeholders use {{name}} syntax; values are HTML-escaped.
 */
@Service
@RequiredArgsConstructor
public class CollegeMailService {

    public record Template(String subject, String body) {}

    public static final Map<String, Template> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("STUDENT_WELCOME", new Template("Your {{collegeName}} placement portal account",
                "<p>Hi {{studentName}},</p><p>{{collegeName}} has added you to the ProveYu placement portal.</p>"
                        + "<p>Sign in at <a href=\"{{portalUrl}}\">{{portalUrl}}</a> with <b>{{email}}</b>. Use <i>Forgot password</i> to set your password.</p>"));
        DEFAULTS.put("DRIVE_PUBLISHED", new Template("New placement drive: {{companyName}} - {{roleTitle}}",
                "<p>Hi {{studentName}},</p><p>You are eligible for <b>{{companyName}}</b> ({{roleTitle}}, {{ctc}}).</p>"
                        + "<p>Register before <b>{{deadline}}</b> in the Campus Drives section of your portal.</p>"));
        DEFAULTS.put("SHORTLISTED", new Template("Shortlisted: {{companyName}} - {{roundName}}",
                "<p>Hi {{studentName}},</p><p>Congratulations! You have been shortlisted for <b>{{roundName}}</b> of the {{companyName}} drive.</p>"));
        DEFAULTS.put("INTERVIEW_SCHEDULED", new Template("Interview scheduled: {{companyName}}",
                "<p>Hi {{studentName}},</p><p>Your interview for <b>{{companyName}}</b> is scheduled on <b>{{when}}</b>.</p>"
                        + "<p>Venue / link: {{where}}</p>"));
        DEFAULTS.put("INTERVIEW_REMINDER", new Template("Reminder: {{companyName}} interview tomorrow",
                "<p>Hi {{studentName}},</p><p>Reminder: your interview for <b>{{companyName}}</b> is on <b>{{when}}</b>. Venue / link: {{where}}</p>"));
        DEFAULTS.put("OFFER_RELEASED", new Template("Offer from {{companyName}}",
                "<p>Hi {{studentName}},</p><p>Congratulations! An offer from <b>{{companyName}}</b> for <b>{{roleTitle}}</b> ({{ctc}}) has been recorded.</p>"
                        + "<p>Please accept or decline it from your portal.</p>"));
        DEFAULTS.put("ANNOUNCEMENT", new Template("{{title}}", "<p>Hi {{studentName}},</p>{{bodyHtml}}"));
    }

    private final EmailTemplateRepository templateRepository;
    private final EmailService emailService;

    public Template resolve(UUID collegeId, String key) {
        return templateRepository.findByCollegeIdAndTemplateKey(collegeId, key)
                .map(t -> new Template(t.getSubject(), t.getBody()))
                .orElse(DEFAULTS.get(key));
    }

    /** vars ending in "Html" are inserted raw (already sanitized by caller); everything else is escaped. */
    public static String render(String text, Map<String, String> vars) {
        String out = text;
        for (var e : vars.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            if (!e.getKey().endsWith("Html")) value = HtmlUtils.htmlEscape(value);
            out = out.replace("{{" + e.getKey() + "}}", value);
        }
        return out;
    }

    public void send(UUID collegeId, String key, String to, Map<String, String> vars) {
        if (to == null || to.isBlank()) return;
        Template t = resolve(collegeId, key);
        if (t == null) return;
        String subject = render(t.subject(), vars).replaceAll("<[^>]*>", "");
        String body = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#1e293b\">" + render(t.body(), vars)
                + "<hr style=\"border:none;border-top:1px solid #e2e8f0\"/><p style=\"font-size:12px;color:#64748b\">Sent via ProveYu</p></div>";
        emailService.sendHtml(to, subject, body);
    }

    public EmailTemplate upsert(UUID collegeId, String key, String subject, String body) {
        EmailTemplate t = templateRepository.findByCollegeIdAndTemplateKey(collegeId, key)
                .orElse(EmailTemplate.builder().collegeId(collegeId).templateKey(key).build());
        t.setSubject(subject);
        t.setBody(body);
        return templateRepository.save(t);
    }
}
