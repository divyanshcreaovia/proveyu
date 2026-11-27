package com.proveyu.cxo.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.proveyu.cxo.domain.CxoModel.Dimension.*;
import static com.proveyu.cxo.domain.CxoModel.WidgetType.*;

/**
 * Turns whatever the model (or a client) sends into a spec the backend can run: unknown metrics are dropped, every dimension,
 * chart type, branch, batch, date and limit is checked against the catalog and the college's own reference data.
 */
@Component
@RequiredArgsConstructor
public class SpecSanitizer {

    static final int MAX_WIDGETS = 12;
    private static final Set<Dimension> FEW_CATEGORIES = Set.of(STATUS, TYPE, CTC_BAND, DIFFICULTY, STAGE, TIER, ROUND, LANGUAGE, GENDER, CGPA_BAND);

    private final MetricCatalog catalog;

    /** Reference data of the signed-in college used to validate filters. */
    public record CollegeRef(List<String> branches, List<Integer> batches) {}

    public DashboardSpec fromJson(JsonNode n, CollegeRef ref) {
        List<WidgetSpec> widgets = new ArrayList<>();
        for (JsonNode w : n.path("widgets")) {
            widgets.add(new WidgetSpec(null, text(w, "title"), enumOf(WidgetType.class, text(w, "type")), text(w, "metric"),
                    enumOf(Dimension.class, text(w, "dimension")), w.path("batchYear").isNumber() ? w.path("batchYear").asInt() : null,
                    text(w, "branchCode"), date(text(w, "from")), date(text(w, "to")), w.path("limit").isNumber() ? w.path("limit").asInt() : null,
                    text(w, "size"), years(w.path("batches"))));
        }
        return clean(new DashboardSpec(text(n, "title"), text(n, "summary"), widgets), ref);
    }

    /** A clean spec: valid widgets only, ids w1..wN, KPI tiles first. Empty widgets list means nothing usable was asked for. */
    public DashboardSpec clean(DashboardSpec in, CollegeRef ref) {
        List<WidgetSpec> kpis = new ArrayList<>(), charts = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>(); // same numbers twice add nothing: keep the first, under a neutral title
        if (in.widgets() != null) {
            for (WidgetSpec w : in.widgets()) {
                if (kpis.size() + charts.size() >= MAX_WIDGETS) break;
                WidgetSpec c = widget(w, ref);
                if (c == null) continue;
                List<WidgetSpec> list = c.type() == KPI ? kpis : charts;
                String sameData = c.metric() + '|' + c.dimension() + '|' + c.batchYear() + '|' + c.batches() + '|' + c.branchCode() + '|' + c.from() + '|' + c.to();
                Integer at = seen.get(sameData);
                if (at != null) {
                    WidgetSpec first = list.get(at);
                    if (!first.title().equalsIgnoreCase(c.title())) list.set(at, withTitle(first, autoTitle(first)));
                    continue;
                }
                seen.put(sameData, list.size());
                list.add(c);
            }
        }
        List<WidgetSpec> all = new ArrayList<>();
        all.addAll(kpis);
        all.addAll(pairHalves(charts));
        List<WidgetSpec> numbered = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            WidgetSpec w = all.get(i);
            numbered.add(new WidgetSpec("w" + (i + 1), w.title(), w.type(), w.metric(), w.dimension(), w.batchYear(), w.branchCode(), w.from(), w.to(),
                    w.limit(), w.size(), w.batches()));
        }
        String title = trim(in.title(), 120);
        return new DashboardSpec(title == null ? "Placement overview" : title, trim(in.summary(), 400), numbered);
    }

    /**
     * For numbers of a dashboard already on screen (live refresh, summary): every widget is validated on its own exactly as in
     * {@link #clean}, but keeps the id the client sent and nothing is merged, added or reordered, so each number lands on the widget
     * that asked for it (even when a filter is no longer valid and two widgets end up asking the same thing). Invalid widgets are
     * left out.
     */
    public List<WidgetSpec> forRun(DashboardSpec in, CollegeRef ref) {
        List<WidgetSpec> out = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (in == null || in.widgets() == null) return out;
        int n = 0;
        for (WidgetSpec w : in.widgets()) {
            if (out.size() >= MAX_WIDGETS) break;
            n++;
            WidgetSpec c = widget(w, ref);
            if (c == null) continue;
            String id = w.id() != null && !w.id().isBlank() && ids.add(w.id()) ? w.id() : "w" + n;
            ids.add(id);
            out.add(new WidgetSpec(id, c.title(), c.type(), c.metric(), c.dimension(), c.batchYear(), c.branchCode(), c.from(), c.to(), c.limit(), c.size(), c.batches()));
        }
        return out;
    }

    private WidgetSpec widget(WidgetSpec w, CollegeRef ref) {
        if (w == null) return null;
        MetricCatalog.Def d = catalog.find(w.metric()).orElse(null);
        if (d == null) return null;
        List<Dimension> allowed = catalog.dimensions(d);
        Dimension dim = w.dimension() != null && allowed.contains(w.dimension()) ? w.dimension() : null;
        WidgetType type = w.type();
        if (type == KPI && allowed.contains(NONE)) dim = NONE; // a KPI tile is one number, whatever split came with it
        if (dim == null) dim = type == KPI && allowed.contains(NONE) ? NONE : allowed.stream().filter(x -> x != NONE).findFirst().orElse(NONE);

        // chart type must fit the dimension
        if (dim == NONE) type = KPI;
        else if (dim == STUDENT) type = TABLE; // a list of names is read, not charted
        else if (type == null || type == KPI) type = dim == MONTH ? LINE : FEW_CATEGORIES.contains(dim) ? DONUT : BAR;
        else if ((type == LINE || type == AREA) && dim != MONTH) type = BAR;
        else if (type == DONUT && dim == MONTH) type = LINE;
        // a donut shows parts of a whole: only counts over a few categories (rates, averages and funnel stages do not add up).
        // Checked last, so it also applies to the type picked by default above.
        if (type == DONUT && (d.unit() != Unit.COUNT || catalog.isFunnel(d) || !FEW_CATEGORIES.contains(dim))) type = BAR;

        // A year the user asked for is kept even when the college has no such batch: the numbers then come back empty ("no data"),
        // never the other batches' numbers. Only an impossible year is dropped.
        Integer batch = validYear(w.batchYear()) ? w.batchYear() : null;
        List<Integer> batches = null;
        if ((dim == BATCH || dim == YEAR) && w.batches() != null) {
            batches = w.batches().stream().filter(SpecSanitizer::validYear).distinct().sorted().limit(8).toList();
            if (batches.isEmpty()) batches = null;
        }
        if (batches != null) batch = null; // a set of years replaces a single one
        String branch = null;
        if (w.branchCode() != null) {
            for (String b : ref.branches()) if (b.equalsIgnoreCase(w.branchCode().trim())) branch = b;
        }
        LocalDate from = w.from(), to = w.to();
        if (from != null && to != null && from.isAfter(to)) { LocalDate t = from; from = to; to = t; }
        // "up to today" would hide what is already scheduled (upcoming drives, interviews): an open end means "until now and beyond"
        // (a whole calendar year asked for keeps its end: "2026" must not show 2027's scheduled drives)
        if (to != null && !to.isBefore(LocalDate.now()) && dim != MONTH && !(to.getMonthValue() == 12 && to.getDayOfMonth() == 31 && from != null)) to = null;
        if (dim == MONTH && from != null) { // a trend spans at most 24 months, so every month still fits on the axis
            LocalDate end = to != null ? to : LocalDate.now();
            if (from.isBefore(end.withDayOfMonth(1).minusMonths(23))) from = end.withDayOfMonth(1).minusMonths(23);
        }
        String size = type == KPI ? "sm" : dim == STUDENT ? "lg" : "lg".equals(w.size()) || "md".equals(w.size()) ? w.size() : "md";
        String title = trim(w.title(), 80);
        if (title == null || !TitleCheck.fits(title, d.key(), d.unit(), dim)) title = autoTitle(d.label(), dim);
        return new WidgetSpec(null, title, type, d.key(), dim, batch, branch, from, to,
                dim == NONE || dim == MONTH ? null : dim == STUDENT ? MetricCatalog.clamp(w.limit(), 3, 50, 50) : MetricCatalog.clamp(w.limit(), 3, 20, 10), size, batches);
    }

    private String autoTitle(WidgetSpec w) {
        return autoTitle(catalog.find(w.metric()).map(MetricCatalog.Def::label).orElse(w.title()), w.dimension());
    }

    private static String autoTitle(String label, Dimension dim) {
        return dim == NONE ? label : dim == MONTH ? label + " per month" : label + " by " + dim.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static WidgetSpec withTitle(WidgetSpec w, String title) {
        return new WidgetSpec(w.id(), title, w.type(), w.metric(), w.dimension(), w.batchYear(), w.branchCode(), w.from(), w.to(), w.limit(), w.size(), w.batches());
    }

    /** Half-width charts sit two to a row; one left alone (next to a full-width chart or at the end) becomes full width. */
    static List<WidgetSpec> pairHalves(List<WidgetSpec> charts) {
        List<WidgetSpec> out = new ArrayList<>(charts);
        Integer open = null;
        for (int i = 0; i < out.size(); i++) {
            if ("md".equals(out.get(i).size())) open = open == null ? i : null;
            else if (open != null) { out.set(open, full(out.get(open))); open = null; }
        }
        if (open != null) out.set(open, full(out.get(open)));
        return out;
    }

    private static WidgetSpec full(WidgetSpec w) {
        return new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(), w.batchYear(), w.branchCode(), w.from(), w.to(), w.limit(), "lg", w.batches());
    }

    // ------------------------------------------------------------------ helpers

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String v) {
        if (v == null) return null;
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate date(String v) {
        if (v == null) return null;
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean validYear(Integer y) {
        return y != null && y >= 2000 && y <= 2100;
    }

    private static List<Integer> years(JsonNode n) {
        if (n == null || !n.isArray()) return null;
        List<Integer> out = new ArrayList<>();
        for (JsonNode y : n) if (y.isNumber()) out.add(y.asInt());
        return out.isEmpty() ? null : out;
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
