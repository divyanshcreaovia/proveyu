package com.proveyu.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private static final String FROM_EMAIL = "info@proveyu.com";
    private static final String SENDER_NAME = "PROVEYU Platform";

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to PROVEYU Skills & Assessment Platform! 🚀");

            String htmlBody = buildWelcomeHtml(fullName);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("[HTML EMAIL SENT] Welcome email delivered to email=[{}]", toEmail);
        } catch (Exception e) {
            log.error("[HTML EMAIL ERROR] Failed to send welcome email to email=[{}]: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject("🔒 PROVEYU - Your Password Reset Verification OTP");

            String htmlBody = buildOtpResetHtml(otp);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("[HTML EMAIL SENT] Password Reset OTP delivered to email=[{}]", toEmail);
        } catch (Exception e) {
            log.error("[HTML EMAIL ERROR] Failed to send OTP email to email=[{}]: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendSlotBookingConfirmationEmail(String toEmail, String fullName, String examTitle, String slotDate) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject("🎟️ PROVEYU - Exam Slot Booking Confirmation");

            String htmlBody = buildSlotBookingHtml(fullName, examTitle, slotDate);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("[HTML EMAIL SENT] Slot booking confirmation delivered to email=[{}]", toEmail);
        } catch (Exception e) {
            log.error("[HTML EMAIL ERROR] Failed to send slot booking email to email=[{}]: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendInterviewInviteEmail(String toCandidateEmail, String candidateName, String recruiterName, String jobTitle, String packageRange) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, SENDER_NAME);
            helper.setTo(toCandidateEmail);
            helper.setSubject("💼 PROVEYU - Interview Invitation: " + jobTitle);

            String htmlBody = buildInterviewInviteHtml(candidateName, recruiterName, jobTitle, packageRange);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("[HTML EMAIL SENT] Interview invitation delivered to candidate email=[{}]", toCandidateEmail);
        } catch (Exception e) {
            log.error("[HTML EMAIL ERROR] Failed to send interview invite email to candidate email=[{}]: {}", toCandidateEmail, e.getMessage());
        }
    }

    @Async
    public void sendAdmitCardIssuedEmail(String toEmail, String fullName, String bookingId, String admitCardDownloadUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject("📄 PROVEYU - Official Examination Admit Card Issued (Booking ID: " + bookingId + ")");

            String htmlBody = buildAdmitCardHtml(fullName, bookingId, admitCardDownloadUrl);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("[HTML EMAIL SENT] Admit Card notification delivered to email=[{}]", toEmail);
        } catch (Exception e) {
            log.error("[HTML EMAIL ERROR] Failed to send Admit Card email to email=[{}]: {}", toEmail, e.getMessage());
        }
    }

    // ==========================================
    // ELEGANT HTML TEMPLATE BUILDERS
    // ==========================================

    private String getHeaderTemplate(String title) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'><style>" +
                "body { font-family: 'Segoe UI', Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 20px; color: #1e293b; }" +
                ".container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.05); border: 1px solid #e2e8f0; }" +
                ".header { background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%); color: #ffffff; padding: 28px 24px; text-align: center; }" +
                ".header h1 { margin: 0; font-size: 24px; font-weight: 700; tracking: 1px; letter-spacing: 0.5px; }" +
                ".header p { margin: 6px 0 0 0; font-size: 13px; opacity: 0.85; text-transform: uppercase; letter-spacing: 1px; }" +
                ".body-content { padding: 32px 28px; font-size: 15px; line-height: 1.6; color: #334155; }" +
                ".footer { background: #f8fafc; padding: 20px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; }" +
                ".footer a { color: #2563eb; text-decoration: none; }" +
                ".badge { display: inline-block; padding: 6px 14px; background: #e0e7ff; color: #3730a3; border-radius: 20px; font-weight: 600; font-size: 13px; font-family: monospace; }" +
                ".btn { display: inline-block; background: #2563eb; color: #ffffff !important; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 14px; margin-top: 16px; box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25); }" +
                ".info-box { background: #f8fafc; border-left: 4px solid #2563eb; padding: 16px 20px; border-radius: 0 8px 8px 0; margin: 20px 0; font-size: 14px; }" +
                "</style></head><body><div class='container'>" +
                "<div class='header'><h1>PROVEYU</h1><p>" + title + "</p></div><div class='body-content'>";
    }

    private String getFooterTemplate() {
        return "</div><div class='footer'>" +
                "<p>© 2026 PROVEYU Assessment & Verification Services. All rights reserved.</p>" +
                "<p>Need assistance? Contact us at <a href='mailto:info@proveyu.com'>info@proveyu.com</a></p>" +
                "</div></div></body></html>";
    }

    private String buildWelcomeHtml(String fullName) {
        return getHeaderTemplate("Skills & Assessment Portal") +
                "<h2>Welcome aboard, " + fullName + "! 👋</h2>" +
                "<p>Thank you for registering with <strong>PROVEYU</strong> — India's premier skill assessment & verified recruitment platform.</p>" +
                "<div class='info-box'>" +
                "<strong>What's Next?</strong><br/>" +
                "1. Complete your candidate profile & upload resume.<br/>" +
                "2. Explore available skill assessment exams.<br/>" +
                "3. Reserve an exam slot to earn your PROVEYU Passport." +
                "</div>" +
                "<p>Ready to get verified?</p>" +
                "<a href='https://proveyu.com/login' class='btn'>Log In to Your Dashboard</a>" +
                getFooterTemplate();
    }

    private String buildOtpResetHtml(String otp) {
        return getHeaderTemplate("Account Security Verification") +
                "<h2>Password Reset Request 🔑</h2>" +
                "<p>We received a request to reset the password for your PROVEYU account.</p>" +
                "<p>Use the following 6-digit verification OTP token to complete your reset request:</p>" +
                "<div style='text-align: center; margin: 28px 0;'>" +
                "<span style='font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #1e3a8a; background: #e0e7ff; padding: 12px 28px; border-radius: 8px; border: 1px dashed #6366f1; display: inline-block;'>" + otp + "</span>" +
                "</div>" +
                "<p style='font-size: 13px; color: #64748b;'><strong>Note:</strong> This OTP token is valid for <strong>15 minutes</strong>. If you did not request this password reset, please ignore this email or contact security immediately.</p>" +
                getFooterTemplate();
    }

    private String buildSlotBookingHtml(String fullName, String examTitle, String slotDate) {
        return getHeaderTemplate("Slot Reservation Confirmation") +
                "<h2>Slot Held Successfully! 🎟️</h2>" +
                "<p>Dear " + fullName + ", your examination slot has been reserved.</p>" +
                "<div class='info-box'>" +
                "<strong>Reservation Summary:</strong><br/>" +
                "• <strong>Exam Title:</strong> " + examTitle + "<br/>" +
                "• <strong>Scheduled Time:</strong> " + slotDate + "<br/>" +
                "• <strong>Status:</strong> <span class='badge'>HELD</span>" +
                "</div>" +
                "<p>Please complete your payment within the reservation window to confirm your booking and generate your Admit Card.</p>" +
                getFooterTemplate();
    }

    private String buildInterviewInviteHtml(String candidateName, String recruiterName, String jobTitle, String packageRange) {
        return getHeaderTemplate("Recruitment Invitation") +
                "<h2>Interview Opportunity! 💼</h2>" +
                "<p>Dear " + candidateName + ",</p>" +
                "<p>Great news! Recruiter <strong>" + recruiterName + "</strong> has reviewed your verified PROVEYU profile and invited you to interview for an open position.</p>" +
                "<div class='info-box'>" +
                "• <strong>Target Role:</strong> " + jobTitle + "<br/>" +
                "• <strong>Salary Offer Range:</strong> " + (packageRange != null ? packageRange : "Competitive LPA") + "<br/>" +
                "• <strong>Recruiter:</strong> " + recruiterName +
                "</div>" +
                "<p>Please log in to your dashboard to review full job requirements and Accept or Decline this invitation.</p>" +
                "<a href='https://proveyu.com/dashboard/interviews' class='btn'>View Invitation Details</a>" +
                getFooterTemplate();
    }

    private String buildAdmitCardHtml(String fullName, String bookingId, String admitCardDownloadUrl) {
        return getHeaderTemplate("Official Examination Hall Ticket") +
                "<h2>Admit Card Issued! 📄</h2>" +
                "<p>Dear " + fullName + ",</p>" +
                "<p>Your payment has been successfully confirmed. Your official PROVEYU Examination Admit Card is ready.</p>" +
                "<div class='info-box'>" +
                "• <strong>Booking Reference:</strong> <span class='badge'>" + bookingId + "</span><br/>" +
                "• <strong>Booking Status:</strong> <span style='color: #16a34a; font-weight: bold;'>CONFIRMED</span><br/>" +
                "• <strong>Format:</strong> High-Resolution Printable PDF" +
                "</div>" +
                "<p>Please click below to view, download, or print your official Admit Card:</p>" +
                "<a href='" + admitCardDownloadUrl + "' class='btn'>Download Admit Card (PDF)</a>" +
                getFooterTemplate();
    }
}
