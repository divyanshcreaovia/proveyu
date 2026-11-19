package com.proveyu.shared.email;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Every e-mail ProveYu sends goes through here, from {@code info@proveyu.com} ("ProveYu"), in the background (@Async): a slow or
 * failing mail server never slows a request down, and a failure is only logged.
 */
@Slf4j
@Service
public class EmailService {

    private static final String FROM_EMAIL = "info@proveyu.com";
    private static final String SENDER_NAME = "ProveYu";

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String contactEmail;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                        @Value("${app.contact-email:support@proveyu.com}") String contactEmail) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.contactEmail = contactEmail;
    }

    /** Generic HTML e-mail with optional attachment (attachment may be null). */
    @Async
    public void sendHtml(String toEmail, String subject, String htmlBody, String attachmentName, byte[] attachment, String attachmentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM_EMAIL, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (attachment != null && attachmentName != null) {
                helper.addAttachment(attachmentName, new org.springframework.core.io.ByteArrayResource(attachment), attachmentType);
            }
            mailSender.send(message);
            log.info("[HTML EMAIL SENT] subject=[{}] to=[{}]", subject, toEmail);
        } catch (Exception e) {
            log.error("[HTML EMAIL ERROR] subject=[{}] to=[{}]: {}", subject, toEmail, e.getMessage());
        }
    }

    @Async
    public void sendHtml(String toEmail, String subject, String htmlBody) {
        sendHtml(toEmail, subject, htmlBody, null, null, null);
    }

    /** After a self-service sign-up (POST /api/v1/auth/register). */
    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        sendHtml(toEmail, "Welcome to ProveYu", buildWelcomeHtml(fullName));
    }

    /** The 6-digit code for a password reset or a first sign-in (student activation, admin-sent reset). */
    @Async
    public void sendPasswordResetOtpEmail(String toEmail, String otp) {
        sendHtml(toEmail, "Your ProveYu verification code", buildOtpResetHtml(otp));
    }

    // ------------------------------------------------------------------ templates

    private String header(String title) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><style>" +
                "body { font-family: 'Segoe UI', Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 20px; color: #1e293b; }" +
                ".container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0; }" +
                ".header { background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%); color: #ffffff; padding: 28px 24px; text-align: center; }" +
                ".header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: 0.5px; }" +
                ".header p { margin: 6px 0 0 0; font-size: 13px; opacity: 0.85; text-transform: uppercase; letter-spacing: 1px; }" +
                ".body-content { padding: 32px 28px; font-size: 15px; line-height: 1.6; color: #334155; }" +
                ".footer { background: #f8fafc; padding: 20px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; }" +
                ".footer a { color: #2563eb; text-decoration: none; }" +
                ".btn { display: inline-block; background: #2563eb; color: #ffffff !important; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 14px; margin-top: 8px; }" +
                ".info-box { background: #f8fafc; border-left: 4px solid #2563eb; padding: 16px 20px; border-radius: 0 8px 8px 0; margin: 20px 0; font-size: 14px; }" +
                "</style></head><body><div class='container'>" +
                "<div class='header'><h1>ProveYu</h1><p>" + title + "</p></div><div class='body-content'>";
    }

    private String footer() {
        return "</div><div class='footer'>" +
                "<p>ProveYu · Campus placements, mock tests and AI interviews in one portal</p>" +
                "<p>Need help? Write to <a href='mailto:" + contactEmail + "'>" + contactEmail + "</a></p>" +
                "</div></div></body></html>";
    }

    private String buildWelcomeHtml(String fullName) {
        return header("Campus placement platform") +
                "<h2>Welcome to ProveYu, " + HtmlUtils.htmlEscape(fullName == null ? "" : fullName) + "!</h2>" +
                "<p>ProveYu brings your college's placement cell and students into one portal: campus drives, applications, round results, " +
                "interviews and offers, plus preparation for the real thing.</p>" +
                "<div class='info-box'>" +
                "<strong>What you can do</strong><br/>" +
                "1. Apply to campus drives you are eligible for and follow every round.<br/>" +
                "2. Practise with the mock test path and AI mock interviews.<br/>" +
                "3. Find off-campus jobs and internships picked for your field." +
                "</div>" +
                "<p>Students get access when their college adds them; sign in with your college e-mail.</p>" +
                "<a href='" + frontendUrl + "/login' class='btn'>Open ProveYu</a>" +
                footer();
    }

    private String buildOtpResetHtml(String otp) {
        return header("Account security") +
                "<h2>Your verification code</h2>" +
                "<p>Use this 6-digit code to set or reset the password of your ProveYu account:</p>" +
                "<div style='text-align: center; margin: 28px 0;'>" +
                "<span style='font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #1e3a8a; background: #e0e7ff; padding: 12px 28px; border-radius: 8px; border: 1px dashed #6366f1; display: inline-block;'>"
                + HtmlUtils.htmlEscape(otp) + "</span>" +
                "</div>" +
                "<p style='font-size: 13px; color: #64748b;'>The code is valid for <strong>15 minutes</strong>. If you did not ask for it, ignore this e-mail: your password stays the same.</p>" +
                footer();
    }
}
