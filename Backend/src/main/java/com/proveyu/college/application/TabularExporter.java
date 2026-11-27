package com.proveyu.college.application;

import com.proveyu.shared.error.DomainException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders a simple table to XLSX, CSV or PDF (landscape A4). */
@Component
public class TabularExporter {

    public record Table(String title, List<String> headers, List<List<Object>> rows) {}

    public record Export(byte[] bytes, String fileName, String contentType) {
        public ResponseEntity<byte[]> toResponse() {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", contentType)
                    .body(bytes);
        }
    }

    public Export export(Table table, String format, String baseName) {
        String fmt = format == null ? "xlsx" : format.toLowerCase(Locale.ROOT);
        String stamp = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        try {
            return switch (fmt) {
                case "csv" -> new Export(csv(table), baseName + "-" + stamp + ".csv", "text/csv");
                case "pdf" -> new Export(pdf(table), baseName + "-" + stamp + ".pdf", "application/pdf");
                case "xlsx" -> new Export(xlsx(table), baseName + "-" + stamp + ".xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                default -> throw new DomainException("Unsupported export format: " + format, HttpStatus.BAD_REQUEST, "BAD_FORMAT");
            };
        } catch (IOException e) {
            throw new DomainException("Export failed", HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED");
        }
    }

    private static String text(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal b) return b.stripTrailingZeros().toPlainString();
        return v.toString();
    }

    public byte[] xlsx(Table t) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(t.title().length() > 30 ? t.title().substring(0, 30) : t.title());
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row hr = sheet.createRow(0);
            for (int i = 0; i < t.headers().size(); i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(t.headers().get(i));
                c.setCellStyle(head);
            }
            int r = 1;
            for (List<Object> row : t.rows()) {
                Row xr = sheet.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    Object v = row.get(i);
                    Cell c = xr.createCell(i);
                    if (v instanceof Number n) c.setCellValue(n.doubleValue());
                    else c.setCellValue(text(v));
                }
            }
            for (int i = 0; i < t.headers().size(); i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] csv(Table t) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM so Excel opens UTF-8 correctly
        try (CSVPrinter p = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            p.printRecord(t.headers());
            for (List<Object> row : t.rows()) p.printRecord(row.stream().map(v -> v instanceof String s ? safeCell(s) : text(v)).toList());
        }
        return out.toByteArray();
    }

    /** A cell that a spreadsheet would run as a formula gets a leading apostrophe. Plain numbers and phone numbers are left alone. */
    static String safeCell(String v) {
        boolean risky = v.length() > 1 && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("[+\\-]?[0-9][0-9 .\\-]*%?");
        return risky ? "'" + v : v;
    }

    public byte[] pdf(Table t) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        float margin = 30, fontSize = 7.5f, rowH = 13;
        int cols = Math.max(1, t.headers().size());
        float colW = (size.getWidth() - 2 * margin) / cols;
        int maxChars = Math.max(4, (int) (colW / (fontSize * 0.5f)));

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int rowIndex = 0;
            int pageNo = 0;
            while (rowIndex < t.rows().size() || pageNo == 0) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
                pageNo++;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    float y = size.getHeight() - margin;
                    write(cs, fontBold, 13, margin, y, t.title());
                    write(cs, font, 8, size.getWidth() - margin - 120, y, "Generated " + LocalDate.now() + "  p." + pageNo);
                    y -= 22;
                    cs.setNonStrokingColor(0.9f, 0.92f, 0.95f);
                    cs.addRect(margin, y - 3, size.getWidth() - 2 * margin, rowH);
                    cs.fill();
                    cs.setNonStrokingColor(0f, 0f, 0f);
                    for (int i = 0; i < cols; i++) write(cs, fontBold, fontSize, margin + i * colW + 2, y, clip(t.headers().get(i), maxChars));
                    y -= rowH;
                    while (rowIndex < t.rows().size() && y > margin) {
                        List<Object> row = t.rows().get(rowIndex++);
                        for (int i = 0; i < Math.min(cols, row.size()); i++) write(cs, font, fontSize, margin + i * colW + 2, y, clip(text(row.get(i)), maxChars));
                        y -= rowH;
                    }
                }
                if (t.rows().isEmpty()) break;
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String clip(String s, int max) {
        String clean = s.replaceAll("[^\\x20-\\x7E]", "?");
        return clean.length() > max ? clean.substring(0, max - 1) + "~" : clean;
    }

    private static void write(PDPageContentStream cs, PDType1Font f, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text.replaceAll("[^\\x20-\\x7E]", "?"));
        cs.endText();
    }
}
