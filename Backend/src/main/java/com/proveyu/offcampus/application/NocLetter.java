package com.proveyu.offcampus.application;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the No Objection Certificate PDF a placement cell issues for an off-campus offer (A4, one page). */
public final class NocLetter {
    private NocLetter() {}

    public record Data(String reference, LocalDate date, String collegeName, String collegeLine, byte[] logo, String studentName, String rollNumber,
                       String degree, String branch, Integer batchYear, String companyName, String roleTitle, String jobType, String ctc,
                       String remarks, String signatoryName, String signatoryEmail) {}

    private static final float MARGIN = 60;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public static byte[] pdf(Data d) throws IOException {
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        PDType1Font small = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float width = page.getMediaBox().getWidth();
            float textWidth = width - 2 * MARGIN;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                float textX = MARGIN;
                if (d.logo() != null) {
                    try {
                        PDImageXObject img = PDImageXObject.createFromByteArray(doc, d.logo(), "logo");
                        float h = 48, w = img.getWidth() * h / Math.max(1, img.getHeight());
                        cs.drawImage(img, MARGIN, y - h + 8, Math.min(w, 96), h);
                        textX = MARGIN + Math.min(w, 96) + 12;
                    } catch (Exception ignored) {
                        // a logo PDFBox cannot read is left out
                    }
                }
                text(cs, bold, 17, textX, y - 10, d.collegeName());
                if (d.collegeLine() != null && !d.collegeLine().isBlank()) text(cs, small, 9.5f, textX, y - 26, d.collegeLine());
                text(cs, small, 9.5f, textX, y - 39, "Training & Placement Cell");
                y -= 58;
                cs.setLineWidth(1.2f);
                cs.moveTo(MARGIN, y);
                cs.lineTo(width - MARGIN, y);
                cs.stroke();

                y -= 26;
                text(cs, regular, 11, MARGIN, y, "Ref: " + d.reference());
                String date = "Date: " + DATE.format(d.date());
                text(cs, regular, 11, width - MARGIN - regular.getStringWidth(safe(date)) / 1000 * 11, y, date);

                y -= 46;
                String title = "NO OBJECTION CERTIFICATE";
                text(cs, bold, 15, (width - bold.getStringWidth(title) / 1000 * 15) / 2, y, title);
                y -= 20;
                text(cs, regular, 11, (width - regular.getStringWidth("TO WHOMSOEVER IT MAY CONCERN") / 1000 * 11) / 2, y, "TO WHOMSOEVER IT MAY CONCERN");

                y -= 34;
                String course = join(" ", d.degree(), d.branch() == null ? null : "(" + d.branch() + ")");
                String p1 = "This is to certify that " + d.studentName() + " (Roll No. " + d.rollNumber() + ")"
                        + (course.isBlank() ? "" : ", pursuing " + course) + (d.batchYear() == null ? "" : ", batch of " + d.batchYear())
                        + ", is a bonafide student of " + d.collegeName() + ".";
                String p2 = "The student has reported an offer for the position of " + d.roleTitle() + " (" + d.jobType() + ") at " + d.companyName()
                        + (d.ctc() == null ? "" : " with a CTC of " + d.ctc()) + ", obtained through an off-campus recruitment process. The college has"
                        + " no objection to the student accepting this offer, subject to the rules of the college and the university.";
                String p3 = "This certificate is issued at the request of the student for submission to " + d.companyName() + ".";
                for (String p : new String[]{p1, p2, d.remarks() == null || d.remarks().isBlank() ? null : "Remarks: " + d.remarks().trim(), p3}) {
                    if (p == null) continue;
                    for (String line : wrap(regular, 12, safe(p), textWidth)) {
                        text(cs, regular, 12, MARGIN, y, line);
                        y -= 18;
                    }
                    y -= 10;
                }

                y -= 50;
                text(cs, bold, 12, MARGIN, y, d.signatoryName());
                text(cs, regular, 11, MARGIN, y - 16, "Training & Placement Officer");
                text(cs, regular, 11, MARGIN, y - 31, d.collegeName());

                String foot = "Issued through ProveYu on " + DATE.format(d.date()) + " · Ref " + d.reference()
                        + (d.signatoryEmail() == null ? "" : " · To verify, write to " + d.signatoryEmail());
                text(cs, small, 8, MARGIN, 40, foot);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void text(PDPageContentStream cs, PDType1Font f, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(s));
        cs.endText();
    }

    static List<String> wrap(PDType1Font f, float size, String text, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (f.getStringWidth(next) / 1000 * size > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** The standard PDF fonts only know Latin-1: drop accents, swap dashes and quotes, replace anything else with "?". */
    static String safe(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('–', '-').replace('—', '-').replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"').replace("…", "...")
                .replace("₹", "Rs. ").replace('·', '-');
        StringBuilder b = new StringBuilder();
        for (char c : n.toCharArray()) b.append(c >= 32 && c <= 255 ? c : '?');
        return b.toString();
    }

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) if (p != null && !p.isBlank()) b.append(b.isEmpty() ? "" : sep).append(p.trim());
        return b.toString();
    }
}
