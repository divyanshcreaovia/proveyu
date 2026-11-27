package com.proveyu.cxo.application;

import com.proveyu.cxo.domain.CxoModel.Benchmark;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Insight;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Board report: the dashboard on screen (or the NIRF/NAAC page) as a PDF or a PowerPoint file. Both are drawn from the same
 * {@link Report}, so the two files say the same thing. Charts are drawn as bars and columns with their values written next to them.
 */
@Component
public class ReportExporter {

    // ------------------------------------------------------------------ the report

    public record Tile(String label, String value, String delta) {}

    public sealed interface Section permits Kpis, Chart, Table, Bullets {}

    public record Kpis(List<Tile> tiles) implements Section {}

    /** @param columns true for a trend over months (vertical columns), else horizontal bars */
    public record Chart(String title, String caption, boolean columns, List<String> labels, List<Double> values, List<String> shown) implements Section {}

    public record Table(String title, List<String> header, List<List<String>> rows, String note) implements Section {}

    public record Bullets(String title, List<String> items) implements Section {}

    public record Report(String title, String subtitle, String scope, String college, List<String> notes, List<Section> sections) {}

    // ------------------------------------------------------------------ building a report

    public Report fromDashboard(String college, DashboardSpec spec, Map<String, WidgetData> data, Insight insight, List<String> notices, String scope) {
        List<Section> sections = new ArrayList<>();
        List<Tile> tiles = new ArrayList<>();
        for (WidgetSpec w : spec.widgets()) {
            WidgetData d = data.get(w.id());
            if (w.type() == WidgetType.KPI && d != null) tiles.add(new Tile(w.title(), format(d.value(), d.unit()), delta(d)));
        }
        if (!tiles.isEmpty()) sections.add(new Kpis(tiles));
        if (insight != null && insight.bullets() != null && !insight.bullets().isEmpty()) {
            List<String> items = new ArrayList<>();
            if (insight.headline() != null && !insight.headline().isBlank()) items.add(insight.headline());
            items.addAll(insight.bullets());
            sections.add(new Bullets("Key takeaways", items));
        }
        for (WidgetSpec w : spec.widgets()) {
            WidgetData d = data.get(w.id());
            if (w.type() == WidgetType.KPI || d == null) continue;
            if (w.dimension() == Dimension.STUDENT) { // names are not put in a report that gets forwarded
                continue;
            }
            List<String> shown = new ArrayList<>();
            for (Double v : d.values()) shown.add(format(v, d.unit()));
            if (d.labels().isEmpty()) { sections.add(new Bullets(w.title(), List.of("No data for this view."))); continue; }
            sections.add(new Chart(w.title(), caption(w), w.dimension() == Dimension.MONTH, d.labels(), d.values(), shown));
        }
        return new Report(spec.title() == null ? "Placement overview" : spec.title(), spec.summary(), scope, college, notices == null ? List.of() : notices, sections);
    }

    public Report fromBenchmark(String college, Benchmark b) {
        List<Section> sections = new ArrayList<>();
        List<String> header = new ArrayList<>(List.of("Field"));
        for (int y : b.years()) header.add("Batch " + y);
        List<List<String>> rows = new ArrayList<>();
        for (var r : b.rows()) {
            List<String> row = new ArrayList<>(List.of(r.label()));
            row.addAll(r.values());
            rows.add(row);
        }
        sections.add(new Table("Graduation outcomes", header, rows, null));
        if (!b.branches().isEmpty()) sections.add(new Table("By branch, batch " + b.year(), b.branchHeader(), b.branches(), null));
        sections.add(new Bullets("Notes", b.notes()));
        return new Report("NIRF / NAAC placement figures, batch " + b.year(), null, "Batches " + b.years().get(0) + " to " + b.year(), college, List.of(), sections);
    }

    private static String caption(WidgetSpec w) {
        if (w.dimension() == Dimension.NONE) return "";
        String by = "by " + w.dimension().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return w.title() != null && w.title().toLowerCase(Locale.ROOT).contains(by) ? "" : by; // not "Offers by company by company"
    }

    private static String delta(WidgetData d) {
        if (d.value() == null || d.previous() == null || d.previousLabel() == null) return null;
        double diff = d.value() - d.previous();
        return (diff > 0 ? "up " : diff < 0 ? "down " : "no change ") + (diff == 0 ? "" : format(Math.abs(diff), d.unit()) + " ") + "vs " + d.previousLabel();
    }

    static String format(Double v, Unit unit) {
        if (v == null) return "–";
        String n = v == Math.rint(v) ? NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(v.longValue()) : String.format(Locale.ROOT, "%.1f", v);
        return unit == Unit.PERCENT ? n + "%" : unit == Unit.LPA ? n + " LPA" : n;
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
    }

    // ------------------------------------------------------------------ PDF

    private static final float W = PDRectangle.A4.getWidth(), H = PDRectangle.A4.getHeight(), M = 40;
    private static final Color INK = new Color(15, 23, 42), DIM = new Color(100, 116, 139), MINT = new Color(16, 185, 129), CARD = new Color(241, 245, 249);

    public byte[] pdf(Report r) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Pdf p = new Pdf(doc, r);
            p.title();
            for (Section s : r.sections()) {
                if (s instanceof Kpis k) p.kpis(k);
                else if (s instanceof Chart c) p.chart(c);
                else if (s instanceof Table t) p.table(t);
                else if (s instanceof Bullets b) p.bullets(b);
            }
            p.finish();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class Pdf {
        final PDDocument doc;
        final Report r;
        final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA), bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDPageContentStream cs;
        float y;
        int pages;

        Pdf(PDDocument doc, Report r) throws IOException {
            this.doc = doc;
            this.r = r;
            page();
        }

        void page() throws IOException {
            if (cs != null) { footer(); cs.close(); }
            PDPage pg = new PDPage(PDRectangle.A4);
            doc.addPage(pg);
            cs = new PDPageContentStream(doc, pg);
            pages++;
            y = H - M;
        }

        void ensure(float need) throws IOException {
            if (y - need < M + 20) page();
        }

        void footer() throws IOException {
            text(r.college() + "  ·  generated " + today() + "  ·  page " + pages, M, 24, regular, 8, DIM);
        }

        void finish() throws IOException {
            footer();
            cs.close();
        }

        String safe(PDType1Font f, String s) {
            StringBuilder b = new StringBuilder();
            for (char ch : (s == null ? "" : s).toCharArray()) {
                try {
                    f.encode(String.valueOf(ch));
                    b.append(ch);
                } catch (Exception e) {
                    b.append(ch == '→' || ch == '▲' ? '>' : ch == '▼' ? '<' : '?');
                }
            }
            return b.toString();
        }

        void text(String s, float x, float yy, PDType1Font f, float size, Color c) throws IOException {
            cs.beginText();
            cs.setFont(f, size);
            cs.setNonStrokingColor(c);
            cs.newLineAtOffset(x, yy);
            cs.showText(safe(f, s));
            cs.endText();
        }

        List<String> wrap(String s, PDType1Font f, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String word : safe(f, s).split("\\s+")) {
                String t = cur.length() == 0 ? word : cur + " " + word;
                if (f.getStringWidth(t) / 1000 * size > width && cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(word); }
                else cur = new StringBuilder(t);
            }
            if (cur.length() > 0) lines.add(cur.toString());
            return lines;
        }

        float width(String s, PDType1Font f, float size) throws IOException {
            return f.getStringWidth(safe(f, s)) / 1000 * size;
        }

        String fit(String s, PDType1Font f, float size, float max) throws IOException {
            String t = safe(f, s);
            while (t.length() > 1 && width(t, f, size) > max) t = t.substring(0, t.length() - 1);
            return t.length() < safe(f, s).length() ? t.substring(0, Math.max(1, t.length() - 1)) + "..." : t;
        }

        void rect(float x, float yy, float w, float h, Color c) throws IOException {
            cs.setNonStrokingColor(c);
            cs.addRect(x, yy, w, h);
            cs.fill();
        }

        void title() throws IOException {
            rect(0, H - 8, W, 8, MINT);
            y -= 14;
            for (String line : wrap(r.title(), bold, 20, W - 2 * M)) { text(line, M, y, bold, 20, INK); y -= 25; }
            if (r.subtitle() != null && !r.subtitle().isBlank()) for (String line : wrap(r.subtitle(), regular, 10.5f, W - 2 * M)) { text(line, M, y, regular, 10.5f, DIM); y -= 14; }
            text(r.college() + "  ·  " + today() + (r.scope() == null || r.scope().isBlank() ? "" : "  ·  " + r.scope()), M, y, regular, 9, DIM);
            y -= 18;
            for (String n : r.notes()) {
                for (String line : wrap(n, regular, 8.5f, W - 2 * M)) { ensure(12); text(line, M, y, regular, 8.5f, DIM); y -= 11; }
            }
            y -= 8;
        }

        void kpis(Kpis k) throws IOException {
            float gap = 10, w = (W - 2 * M - 3 * gap) / 4, h = 58;
            for (int i = 0; i < k.tiles().size(); i++) {
                if (i % 4 == 0) { ensure(h + gap); if (i > 0) y -= h + gap; }
                Tile t = k.tiles().get(i);
                float x = M + (i % 4) * (w + gap), top = y;
                rect(x, top - h, w, h, CARD);
                rect(x, top - h, 3, h, MINT);
                text(fit(t.label(), regular, 8.5f, w - 14), x + 10, top - 14, regular, 8.5f, DIM);
                text(fit(t.value(), bold, 17, w - 14), x + 10, top - 34, bold, 17, INK);
                if (t.delta() != null) text(fit(t.delta(), regular, 7.5f, w - 14), x + 10, top - 49, regular, 7.5f, DIM);
            }
            y -= h + 18;
        }

        void bullets(Bullets b) throws IOException {
            ensure(30);
            text(b.title(), M, y, bold, 12, INK);
            y -= 16;
            for (String item : b.items()) {
                List<String> lines = wrap(item, regular, 10, W - 2 * M - 14);
                ensure(lines.size() * 13 + 4);
                text("-", M, y, bold, 10, MINT);
                for (String line : lines) { text(line, M + 12, y, regular, 10, INK); y -= 13; }
                y -= 3;
            }
            y -= 10;
        }

        void chart(Chart c) throws IOException {
            int n = Math.min(c.labels().size(), c.columns() ? 24 : 12);
            double max = c.values().stream().filter(v -> v != null).mapToDouble(Double::doubleValue).max().orElse(1);
            if (max <= 0) max = 1;
            float need = c.columns() ? 170 : 34 + n * 17;
            ensure(need);
            text(c.title(), M, y, bold, 11.5f, INK);
            if (c.caption() != null && !c.caption().isBlank()) text(c.caption(), M + width(c.title(), bold, 11.5f) + 8, y, regular, 8.5f, DIM);
            y -= 14;
            if (c.columns()) {
                float area = 110, slot = (W - 2 * M) / n, bw = Math.min(22, slot * 0.6f), base = y - area - 14;
                for (int i = 0; i < n; i++) {
                    Double v = c.values().get(i);
                    float bh = v == null ? 0 : (float) (v / max * area);
                    float x = M + i * slot + (slot - bw) / 2;
                    rect(x, base, bw, Math.max(bh, v != null && v > 0 ? 1 : 0), MINT);
                    if (v != null && n <= 14) text(c.shown().get(i), x + bw / 2 - width(c.shown().get(i), regular, 7) / 2, base + bh + 3, regular, 7, INK);
                    String l = c.labels().get(i);
                    String lab = l.length() == 7 && l.charAt(4) == '-' ? l.substring(2) : l;
                    text(fit(lab, regular, 7, slot), M + i * slot + (slot - width(fit(lab, regular, 7, slot), regular, 7)) / 2, base - 10, regular, 7, DIM);
                }
                y = base - 24;
            } else {
                float labelW = 130, barMax = W - 2 * M - labelW - 70;
                for (int i = 0; i < n; i++) {
                    Double v = c.values().get(i);
                    text(fit(c.labels().get(i), regular, 8.5f, labelW - 6), M, y - 9, regular, 8.5f, INK);
                    float bw = v == null ? 0 : (float) (v / max * barMax);
                    rect(M + labelW, y - 11, Math.max(bw, v != null && v > 0 ? 1.5f : 0), 11, MINT);
                    text(c.shown().get(i), M + labelW + bw + 5, y - 9, bold, 8.5f, INK);
                    y -= 17;
                }
                y -= 8;
            }
            y -= 8;
        }

        void table(Table t) throws IOException {
            ensure(50);
            text(t.title(), M, y, bold, 11.5f, INK);
            y -= 16;
            int cols = t.header().size();
            float first = cols > 4 ? 130 : 170, other = (W - 2 * M - first) / Math.max(1, cols - 1);
            row(t.header(), first, other, true);
            for (List<String> r2 : t.rows()) row(r2, first, other, false);
            y -= 10;
        }

        void row(List<String> cells, float first, float other, boolean head) throws IOException {
            ensure(18);
            if (head) rect(M, y - 4, W - 2 * M, 16, CARD);
            float x = M;
            for (int i = 0; i < cells.size(); i++) {
                float w = i == 0 ? first : other;
                text(fit(cells.get(i), head || i == 0 ? bold : regular, 8.5f, w - 6), x + 3, y, head || i == 0 ? bold : regular, 8.5f, INK);
                x += w;
            }
            y -= 16;
        }
    }

    // ------------------------------------------------------------------ PowerPoint

    private static final Color P_INK = new Color(15, 23, 42), P_DIM = new Color(100, 116, 139), P_MINT = new Color(16, 185, 129), P_CARD = new Color(241, 245, 249);

    public byte[] pptx(Report r) {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new java.awt.Dimension(960, 540));
            XSLFSlide cover = ppt.createSlide();
            band(cover);
            text(cover, r.title(), 60, 150, 840, 110, 36, true, P_INK);
            if (r.subtitle() != null && !r.subtitle().isBlank()) text(cover, r.subtitle(), 60, 270, 840, 60, 16, false, P_DIM);
            text(cover, r.college() + "  ·  " + today() + (r.scope() == null || r.scope().isBlank() ? "" : "  ·  " + r.scope()), 60, 350, 840, 30, 14, false, P_DIM);
            int y0 = 390;
            for (String n : r.notes()) { text(cover, n, 60, y0, 840, 36, 11, false, P_DIM); y0 += 32; if (y0 > 500) break; }

            for (Section s : r.sections()) {
                if (s instanceof Kpis k) {
                    for (int from = 0; from < k.tiles().size(); from += 8) {
                        XSLFSlide sl = slide(ppt, "Key numbers");
                        List<Tile> part = k.tiles().subList(from, Math.min(k.tiles().size(), from + 8));
                        for (int i = 0; i < part.size(); i++) {
                            int x = 40 + (i % 4) * 225, yy = 120 + (i / 4) * 170;
                            box(sl, x, yy, 210, 150, P_CARD);
                            box(sl, x, yy, 5, 150, P_MINT);
                            text(sl, part.get(i).label(), x + 16, yy + 12, 185, 40, 12, false, P_DIM);
                            text(sl, part.get(i).value(), x + 16, yy + 55, 185, 50, 28, true, P_INK);
                            if (part.get(i).delta() != null) text(sl, part.get(i).delta(), x + 16, yy + 112, 185, 30, 10, false, P_DIM);
                        }
                    }
                } else if (s instanceof Chart c) {
                    XSLFSlide sl = slide(ppt, c.title() + (c.caption() == null || c.caption().isBlank() ? "" : "  (" + c.caption() + ")"));
                    chartSlide(sl, c);
                } else if (s instanceof Table t) {
                    for (int from = 0; from < Math.max(1, t.rows().size()); from += 12) {
                        XSLFSlide sl = slide(ppt, t.title());
                        XSLFTable tab = sl.createTable();
                        int cols = t.header().size();
                        tab.setAnchor(new Rectangle(40, 110, 880, 380));
                        for (int i = 0; i < cols; i++) tab.setColumnWidth(i, i == 0 ? (cols > 4 ? 220 : 330) : (880.0 - (cols > 4 ? 220 : 330)) / Math.max(1, cols - 1));
                        row(tab, t.header(), true);
                        for (List<String> rr : t.rows().subList(from, Math.min(t.rows().size(), from + 12))) row(tab, rr, false);
                    }
                } else if (s instanceof Bullets b) {
                    XSLFSlide sl = slide(ppt, b.title());
                    XSLFTextBox box = sl.createTextBox();
                    box.setAnchor(new Rectangle(50, 110, 860, 390));
                    for (String item : b.items()) {
                        XSLFTextParagraph para = box.addNewTextParagraph();
                        para.setBullet(true);
                        para.setSpaceAfter(8.0);
                        XSLFTextRun run = para.addNewTextRun();
                        run.setText(item);
                        run.setFontSize(16.0);
                        run.setFontColor(P_INK);
                    }
                }
            }
            ppt.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void chartSlide(XSLFSlide sl, Chart c) {
        int n = Math.min(c.labels().size(), c.columns() ? 24 : 12);
        double max = c.values().stream().filter(v -> v != null).mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) max = 1;
        if (c.columns()) {
            double slot = 880.0 / n, area = 290;
            for (int i = 0; i < n; i++) {
                Double v = c.values().get(i);
                double bh = v == null ? 0 : v / max * area;
                int x = (int) (40 + i * slot + slot * 0.15), w = (int) Math.max(4, slot * 0.7);
                if (bh > 0) box(sl, x, (int) (420 - bh), w, (int) Math.max(2, bh), P_MINT);
                if (n <= 14) text(sl, c.shown().get(i), x - 8, (int) (420 - bh - 22), w + 16, 20, 10, true, P_INK);
                String l = c.labels().get(i);
                text(sl, l.length() == 7 && l.charAt(4) == '-' ? l.substring(2) : l, x - 8, 425, w + 16, 20, 9, false, P_DIM);
            }
        } else {
            double rowH = Math.min(30, 360.0 / Math.max(1, n));
            for (int i = 0; i < n; i++) {
                Double v = c.values().get(i);
                int yy = (int) (120 + i * rowH);
                text(sl, c.labels().get(i), 40, yy, 230, (int) rowH, 12, false, P_INK);
                double bw = v == null ? 0 : v / max * 520;
                if (bw > 0) box(sl, 280, yy + 4, (int) Math.max(3, bw), (int) rowH - 10, P_MINT);
                text(sl, c.shown().get(i), (int) (290 + bw), yy, 120, (int) rowH, 12, true, P_INK);
            }
        }
    }

    private XSLFSlide slide(XMLSlideShow ppt, String title) {
        XSLFSlide sl = ppt.createSlide();
        band(sl);
        text(sl, title, 40, 30, 880, 60, 24, true, P_INK);
        return sl;
    }

    private static void band(XSLFSlide sl) {
        box(sl, 0, 0, 960, 8, P_MINT);
    }

    private static XSLFAutoShape box(XSLFSlide sl, int x, int y, int w, int h, Color c) {
        XSLFAutoShape s = sl.createAutoShape();
        s.setAnchor(new Rectangle(x, y, w, h));
        s.setFillColor(c);
        s.setLineColor(c);
        return s;
    }

    private static void text(XSLFSlide sl, String t, int x, int y, int w, int h, double size, boolean bold, Color c) {
        XSLFTextBox b = sl.createTextBox();
        b.setAnchor(new Rectangle(x, y, w, h));
        b.setWordWrap(true);
        XSLFTextParagraph p = b.addNewTextParagraph();
        p.setTextAlign(TextParagraph.TextAlign.LEFT);
        XSLFTextRun run = p.addNewTextRun();
        run.setText(t == null ? "" : t);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(c);
    }

    private static void row(XSLFTable tab, List<String> cells, boolean head) {
        XSLFTableRow row = tab.addRow();
        row.setHeight(head ? 30 : 26);
        for (String cell : cells) {
            XSLFTableCell tc = row.addCell();
            XSLFTextParagraph p = tc.addNewTextParagraph();
            XSLFTextRun run = p.addNewTextRun();
            run.setText(cell == null ? "" : cell);
            run.setFontSize(head ? 12.0 : 11.0);
            run.setBold(head);
            run.setFontColor(P_INK);
            tc.setFillColor(head ? P_CARD : Color.WHITE);
        }
    }
}
