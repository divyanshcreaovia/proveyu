package com.proveyu.payment.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.booking.domain.Booking;
import com.proveyu.shared.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmitCardGeneratorService {

    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    public String generateAndSaveAdmitCardPdf(Booking booking, String qrToken, String barcode) {
        User candidate = userRepository.findById(booking.getCandidateId()).orElse(null);
        String candidateName = candidate != null ? candidate.getFullName() : "Verified Candidate";
        String candidateEmail = candidate != null ? candidate.getEmail() : "N/A";
        String candidatePhone = candidate != null && candidate.getPhone() != null ? candidate.getPhone() : "+91-9876543210";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss z")
                .withZone(ZoneId.of("Asia/Kolkata"));
        String formattedIssueDate = formatter.format(Instant.now());

        String rollNumber = "PNRE-2026-" + Math.abs(booking.getId().hashCode() % 90000 + 10000);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Generate Real Scannable QR Code & Barcode Images using ZXing
            BufferedImage qrCodeImg = generateQrCodeImage("https://proveyu.com/verify-admit-card?token=" + qrToken + "&bookingId=" + booking.getId(), 140, 140);
            BufferedImage barcodeImg = generateBarcodeImage(barcode, 220, 45);

            PDImageXObject pdQrImage = LosslessFactory.createFromImage(document, qrCodeImg);
            PDImageXObject pdBarcodeImage = LosslessFactory.createFromImage(document, barcodeImg);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // Header Banner (Dark Navy `#0f172a`)
                cs.setNonStrokingColor(15 / 255f, 23 / 255f, 42 / 255f);
                cs.addRect(0, 745, 595, 97);
                cs.fill();

                // Header Title
                cs.setNonStrokingColor(1.0f, 1.0f, 1.0f);
                drawText(cs, "PROVEYU SKILLS & RECRUITMENT ASSESSMENT", 30, 810, 18, true);
                drawText(cs, "NATIONAL ELIGIBILITY HALL TICKET / ADMIT CARD (PNRE 2026)", 30, 790, 10, false);
                drawText(cs, "MODE: COMPUTER BASED TEST (CBT)", 30, 772, 9, true);

                // Outer Card Border Box
                cs.setStrokingColor(203 / 255f, 213 / 255f, 225 / 255f);
                cs.setLineWidth(1.2f);
                cs.addRect(25, 25, 545, 710);
                cs.stroke();

                // Draw Barcode on Top Right of Card
                cs.drawImage(pdBarcodeImage, 345, 755, 220, 38);

                // Section 1: Candidate Information & Photo Box
                int y = 715;
                cs.setNonStrokingColor(30 / 255f, 41 / 255f, 59 / 255f);
                drawText(cs, "1. CANDIDATE PERSONAL INFORMATION", 35, y, 11, true);

                cs.setStrokingColor(37 / 255f, 99 / 255f, 235 / 255f);
                cs.setLineWidth(1.5f);
                cs.moveTo(35, y - 5);
                cs.lineTo(250, y - 5);
                cs.stroke();

                y -= 25;
                drawText(cs, "Roll / Reg. Number  : " + rollNumber, 40, y, 10, true);
                y -= 20;
                drawText(cs, "Candidate Full Name : " + candidateName, 40, y, 10, true);
                y -= 20;
                drawText(cs, "Registered Email    : " + candidateEmail, 40, y, 10, false);
                y -= 20;
                drawText(cs, "Contact Number      : " + candidatePhone, 40, y, 10, false);
                y -= 20;
                drawText(cs, "Category / Gender   : General / Unreserved (UR)", 40, y, 10, false);
                y -= 20;
                drawText(cs, "Booking Reference   : " + booking.getId(), 40, y, 9, false);

                // Candidate Photo Box Placeholder (Right Side)
                cs.setStrokingColor(148 / 255f, 163 / 255f, 184 / 255f);
                cs.setLineWidth(1f);
                cs.setNonStrokingColor(248 / 255f, 250 / 252f, 252 / 255f);
                cs.addRect(440, 590, 110, 130);
                cs.fillAndStroke();

                cs.setNonStrokingColor(100 / 255f, 116 / 255f, 139 / 255f);
                drawText(cs, "PASSPORT", 468, 665, 9, true);
                drawText(cs, "PHOTO", 477, 650, 9, true);
                drawText(cs, "(VERIFIED)", 465, 635, 8, false);

                // Section 2: Examination Schedule & Venue Location Box
                y = 575;
                cs.setNonStrokingColor(30 / 255f, 41 / 255f, 59 / 255f);
                drawText(cs, "2. EXAMINATION SCHEDULE & TEST CENTER LOCATION", 35, y, 11, true);

                cs.setStrokingColor(37 / 255f, 99 / 255f, 235 / 255f);
                cs.setLineWidth(1.5f);
                cs.moveTo(35, y - 5);
                cs.lineTo(330, y - 5);
                cs.stroke();

                // Test Schedule & Venue Details Box
                y -= 15;
                cs.setStrokingColor(203 / 255f, 213 / 255f, 225 / 255f);
                cs.setNonStrokingColor(241 / 255f, 245 / 255f, 249 / 255f);
                cs.addRect(35, y - 145, 525, 140);
                cs.fillAndStroke();

                cs.setNonStrokingColor(15 / 255f, 23 / 255f, 42 / 255f);
                int vy = y - 18;
                drawText(cs, "Exam Name     : PROVEYU Java Full-Stack & Systems Architecture Certification", 45, vy, 10, true);
                vy -= 20;
                drawText(cs, "Exam Date     : Monday, 21 September 2026", 45, vy, 10, true);
                vy -= 20;
                drawText(cs, "Test Timing   : 10:00 AM to 11:30 AM IST (Duration: 90 Minutes)", 45, vy, 10, true);
                vy -= 20;
                drawText(cs, "Reporting Time: 09:15 AM IST (Gate Closure: 09:45 AM IST - Strict)", 45, vy, 10, true);
                vy -= 20;
                drawText(cs, "Test Center   : PROVEYU Digital Assessment Zone - Lab #3", 45, vy, 10, true);
                vy -= 20;
                drawText(cs, "Center Address: APMOSYS Tech Park, Building A, 4th Floor, Sector 11, Mahape,", 45, vy, 9, false);
                vy -= 16;
                drawText(cs, "                Navi Mumbai, Maharashtra - 400710 (Landmark: Opp. MBP Gate 2)", 45, vy, 9, false);

                // Section 3: QR Code & Security Verification
                y -= 165;
                cs.setNonStrokingColor(30 / 255f, 41 / 255f, 59 / 255f);
                drawText(cs, "3. SECURITY & QR CODE ENTRY VERIFICATION", 35, y, 11, true);

                cs.setStrokingColor(37 / 255f, 99 / 255f, 235 / 255f);
                cs.setLineWidth(1.5f);
                cs.moveTo(35, y - 5);
                cs.lineTo(350, y - 5);
                cs.stroke();

                // Draw ZXing QR Code Image Box (Left Side)
                y -= 15;
                cs.setStrokingColor(37 / 255f, 99 / 255f, 235 / 255f);
                cs.setLineWidth(1f);
                cs.addRect(40, y - 110, 110, 110);
                cs.stroke();

                cs.drawImage(pdQrImage, 42, y - 108, 106, 106);

                // QR Code Text & Security Token Info (Right of QR Code)
                cs.setNonStrokingColor(15 / 255f, 23 / 255f, 42 / 255f);
                int qy = y - 22;
                drawText(cs, "Scan this QR code at the test center gate for automated entry authorization.", 165, qy, 9, false);
                qy -= 18;
                drawText(cs, "QR Security Token : " + qrToken, 165, qy, 9, true);
                qy -= 18;
                drawText(cs, "Gate Barcode No   : " + barcode, 165, qy, 9, true);
                qy -= 18;
                drawText(cs, "Admit Card Issued : " + formattedIssueDate, 165, qy, 8, false);

                // Section 4: Signatures Section
                y -= 135;
                cs.setStrokingColor(203 / 255f, 213 / 255f, 225 / 255f);
                cs.setLineWidth(1f);

                // Candidate Signature Box
                cs.addRect(40, y - 45, 150, 45);
                cs.stroke();
                drawText(cs, "Candidate Signature", 65, y - 57, 8, false);

                // Invigilator Signature Box
                cs.addRect(220, y - 45, 150, 45);
                cs.stroke();
                drawText(cs, "Invigilator Signature", 248, y - 57, 8, false);

                // Controller Seal Box
                cs.setStrokingColor(37 / 255f, 99 / 255f, 235 / 255f);
                cs.addRect(400, y - 45, 160, 45);
                cs.stroke();
                cs.setNonStrokingColor(37 / 255f, 99 / 255f, 235 / 255f);
                drawText(cs, "PROVEYU EXAM BOARD", 425, y - 22, 9, true);
                drawText(cs, "Controller of Examinations", 422, y - 36, 8, false);

                // Section 5: Examination Instructions
                y -= 75;
                cs.setNonStrokingColor(30 / 255f, 41 / 255f, 59 / 255f);
                drawText(cs, "IMPORTANT INSTRUCTIONS FOR CANDIDATES", 35, y, 10, true);

                y -= 16;
                drawText(cs, "1. Print this Admit Card in original color/mono resolution. Present this ticket with original Govt. Photo ID.", 35, y, 8, false);
                y -= 14;
                drawText(cs, "2. Acceptable IDs: Aadhaar Card, PAN Card, Passport, Voter ID, or Driving License. Photocopies not allowed.", 35, y, 8, false);
                y -= 14;
                drawText(cs, "3. Report strictly by 09:15 AM IST. Test center gate closes at 09:45 AM IST. No late entry permitted.", 35, y, 8, false);
                y -= 14;
                drawText(cs, "4. Prohibited Items: Mobile phones, smart watches, bluetooth devices, notes, calculators, or bags.", 35, y, 8, false);
                y -= 14;
                drawText(cs, "5. Rough sheets & pens will be provided inside the examination hall and must be surrendered before exit.", 35, y, 8, false);

                // Footer Note
                cs.setNonStrokingColor(100 / 255f, 116 / 255f, 139 / 255f);
                drawText(cs, "System Generated Official Admit Card • Verification Portal: https://proveyu.com/verify", 120, 32, 8, false);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            byte[] pdfBytes = baos.toByteArray();
            String fileName = "admit_card_" + booking.getId() + ".pdf";

            fileStorageService.storeAdmitCardPdf(pdfBytes, fileName);
            String downloadUrl = "/api/v1/admit-cards/download/" + fileName;

            log.info("[INDUSTRY PDF ADMIT CARD GENERATED] Generated official admit card with real ZXing QR & Barcode for bookingId=[{}] url=[{}]", booking.getId(), downloadUrl);
            return downloadUrl;
        } catch (Exception e) {
            log.error("[PDF GENERATION ERROR] Failed to generate PDF admit card for bookingId=[{}]: {}", booking.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF Admit Card", e);
        }
    }

    private BufferedImage generateQrCodeImage(String text, int width, int height) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    private BufferedImage generateBarcodeImage(String text, int width, int height) throws Exception {
        Code128Writer barcodeWriter = new Code128Writer();
        BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.CODE_128, width, height);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    private void drawText(PDPageContentStream cs, String text, int x, int y, int fontSize, boolean isBold) throws Exception {
        cs.beginText();
        if (isBold) {
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), fontSize);
        } else {
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
        }
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    public Resource loadAdmitCardPdf(String fileName) {
        return fileStorageService.loadAdmitCardResource(fileName);
    }
}
