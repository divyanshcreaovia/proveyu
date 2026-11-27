package com.proveyu.cxo.application;

import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.proveyu.cxo.domain.CxoModel.Dimension.*;
import static com.proveyu.cxo.domain.CxoModel.WidgetType.*;

/**
 * Makes sure a dashboard shows exactly the years the person asked for, whatever the planner (model or keywords) produced.
 * <ul>
 *   <li>one year: every number is that year only. For numbers tied to students it is the graduating batch of that year, for numbers
 *       tied only to a date (drives, interviews, announcements) the calendar year. A year with no batch gives no numbers at all.</li>
 *   <li>several years, a range, "all years" or "vs last year": one chart per metric with exactly those years side by side;
 *       a year with nothing recorded is simply not drawn.</li>
 *   <li>no year in the question: the plan is left as it is.</li>
 * </ul>
 * The question is read by this class, not by the model, so the filter cannot be forgotten or invented.
 */
@Component
@RequiredArgsConstructor
public class ScopeEnforcer {

    /** @param years years named in the question (ranges expanded), @param all "all years / every batch", @param previous "vs last year" */
    public record Scope(List<Integer> years, boolean all, boolean previous) {
        public boolean any() { return !years.isEmpty() || all; }
    }

    private static final Pattern YEAR_RE = Pattern.compile("(?<![\\d.])(20\\d{2})(?![\\d])");
    private static final Pattern RANGE = Pattern.compile("(?<!\\d)(20\\d{2})\\s*(?:to|till|until|through|se|-|–|—)\\s*(20\\d{2})(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN = Pattern.compile("(?<!\\d)between\\s+(20\\d{2})\\s+(?:and|aur|&)\\s+(20\\d{2})(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINCE = Pattern.compile("\\b(?:since|from|starting|after)\\s+(?:the\\s+)?(?:year\\s+)?(20\\d{2})\\b(?!\\s*(?:to|till|until|through|se|-|–|—|and)\\s*20\\d{2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALL = Pattern.compile("\\b(all|every|each|sab|saare|sabhi|har)\\s+(the\\s+)?(years?|batch(?:es)?|saal|sal)\\b"
            + "|\\bacross\\s+(the\\s+)?(years|batches)\\b|\\byear[\\s-]*(on|over)[\\s-]*year\\b|\\byoy\\b|\\bbatch[\\s-]*wise\\b|\\byear[\\s-]*wise\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PREVIOUS = Pattern.compile("(?<!the\\s)\\b(last|previous|prior|pichhle|pichle|pichhla|pichla)\\s+(year|batch|saal|sal)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern THIS = Pattern.compile("\\b(this|current)\\s+(year|batch|season)\\b|\\b(is|ye|chalu)\\s+(saal|sal|batch)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSUS = Pattern.compile("\\b(vs\\.?|versus|compare|compared|comparison|growth|improve\\w*|se compare|badha|ghata|badhi|ghati|difference)\\b", Pattern.CASE_INSENSITIVE);

    private final MetricCatalog catalog;
    private final SpecSanitizer sanitizer;

    public static Scope parse(String prompt) {
        TreeSet<Integer> years = new TreeSet<>();
        String text = prompt == null ? "" : prompt;
        Matcher r = RANGE.matcher(text);
        while (r.find()) {
            int a = Integer.parseInt(r.group(1)), b = Integer.parseInt(r.group(2));
            if (b > a && b - a <= 10) for (int y = a; y <= b; y++) years.add(y);
        }
        Matcher between = BETWEEN.matcher(text);
        while (between.find()) {
            int a = Integer.parseInt(between.group(1)), b = Integer.parseInt(between.group(2));
            if (b > a && b - a <= 10) for (int y = a; y <= b; y++) years.add(y);
        }
        Matcher since = SINCE.matcher(text);
        while (since.find()) { // "since 2023": every year from then until now
            int from = Integer.parseInt(since.group(1)), now = LocalDate.now().getYear();
            for (int y = Math.max(from, now - 10); y <= Math.max(from, now); y++) years.add(y);
        }
        Matcher m = YEAR_RE.matcher(text);
        while (m.find()) years.add(Integer.parseInt(m.group(1)));
        boolean previous = PREVIOUS.matcher(text).find();
        if (years.isEmpty() && !ALL.matcher(text).find()) { // "this year", "is saal", "last year", "pichle saal" without a number
            int now = LocalDate.now().getYear();
            boolean cur = THIS.matcher(text).find();
            if (cur && previous) { years.add(now - 1); years.add(now); }
            else if (cur) years.add(now);
            else if (previous) { if (VERSUS.matcher(text).find()) { years.add(now - 1); years.add(now); } else years.add(now - 1); }
            previous = false; // the years are now explicit
        }
        return new Scope(new ArrayList<>(years), ALL.matcher(text).find(), previous);
    }

    /** The years to put side by side, or empty when the question names one year (or none). */
    static List<Integer> compared(Scope s, CollegeRef ref) {
        if (s.all()) {
            TreeSet<Integer> all = new TreeSet<>(ref.batches());
            all.addAll(s.years());
            return new ArrayList<>(all);
        }
        if (s.years().size() >= 2) return s.years();
        if (s.years().size() == 1 && s.previous()) return List.of(s.years().get(0) - 1, s.years().get(0));
        return List.of();
    }

    public DashboardSpec apply(DashboardSpec spec, String prompt, CollegeRef ref) {
        Scope scope = parse(prompt);
        if (!scope.any() || spec.widgets() == null || spec.widgets().isEmpty()) return spec;
        List<Integer> years = compared(scope, ref);
        DashboardSpec out = years.isEmpty() ? single(spec, scope.years().get(0)) : side(spec, years, scope.all());
        return sanitizer.clean(out, ref);
    }

    private DashboardSpec single(DashboardSpec spec, int year) {
        List<WidgetSpec> out = new ArrayList<>();
        for (WidgetSpec w : spec.widgets()) {
            MetricCatalog.Def d = catalog.find(w.metric()).orElse(null);
            if (d == null || w.dimension() == BATCH || w.dimension() == YEAR) continue; // one year: nothing to put side by side
            if (d.batchCol() != null) {
                out.add(new WidgetSpec(null, w.title(), w.type(), w.metric(), w.dimension(), year, w.branchCode(), null, null, w.limit(), w.size()));
            } else if (d.dateCol() != null) {
                out.add(new WidgetSpec(null, w.title(), w.type(), w.metric(), w.dimension(), null, w.branchCode(), LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31), w.limit(), w.size()));
            } // a number tied to neither a batch nor a date (the recruiter list) cannot be limited to a year: left out
        }
        if (out.isEmpty()) out = defaults(year);
        boolean byBatch = out.stream().anyMatch(w -> w.batchYear() != null);
        return new DashboardSpec(scoped(spec.title(), byBatch ? "batch " + year : String.valueOf(year)), spec.summary(), out);
    }

    private DashboardSpec side(DashboardSpec spec, List<Integer> years, boolean all) {
        List<WidgetSpec> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (WidgetSpec w : spec.widgets()) {
            MetricCatalog.Def d = catalog.find(w.metric()).orElse(null);
            if (d == null || catalog.isFunnel(d) || w.dimension() == STUDENT || "offers_by_ctc".equals(w.metric()) || !seen.add(w.metric())) continue; // (offers by package by batch repeats offers by batch)
            Dimension dim = d.batchCol() != null && catalog.dimensions(d).contains(BATCH) ? BATCH : d.dateCol() != null ? YEAR : null;
            if (dim == null) continue;
            out.add(new WidgetSpec(null, d.label() + (dim == BATCH ? " by batch" : " by year"), COLUMN, d.key(), dim, null, w.branchCode(), null, null, null,
                    "md").withBatches(years));
            if (out.size() >= 8) break;
        }
        if (out.isEmpty()) {
            for (String key : List.of("placement_rate", "offers", "avg_ctc", "applications")) {
                MetricCatalog.Def d = catalog.find(key).orElseThrow();
                out.add(new WidgetSpec(null, d.label() + " by batch", COLUMN, key, BATCH, null, null, null, null, null, "md").withBatches(years));
            }
        }
        String label = all ? "all batches" : "batches " + String.join(", ", years.stream().map(String::valueOf).toList());
        return new DashboardSpec(scoped(spec.title(), label), spec.summary(), out);
    }

    private List<WidgetSpec> defaults(int year) {
        List<WidgetSpec> w = new ArrayList<>();
        for (String[] k : new String[][]{{"Students", "students_total"}, {"Placed", "placed_students"}, {"Placement rate", "placement_rate"}, {"Average CTC", "avg_ctc"}}) {
            w.add(new WidgetSpec(null, k[0], KPI, k[1], NONE, year, null, null, null, null, "sm"));
        }
        return w;
    }

    static String scoped(String title, String scope) {
        List<String> keep = new ArrayList<>(); // the parts of the title that are not a batch scope from an earlier question
        for (String part : (title == null ? "Placement overview" : title).split("·")) {
            String t = part.trim();
            if (!t.isEmpty() && !t.toLowerCase(Locale.ROOT).startsWith("batch") && !t.toLowerCase(Locale.ROOT).startsWith("all batches") && !t.matches("20\\d{2}")) keep.add(t);
        }
        String base = DashboardPlanner.baseTitle(String.join(" · ", keep.isEmpty() ? List.of("Placement overview") : keep));
        // a title that already names the year ("2025 Batch Placement Overview") is not given the year twice
        java.util.regex.Matcher in = Pattern.compile("20\\d{2}").matcher(scope);
        if (in.find() && base.contains(in.group())) return base;
        return base + " · " + scope;
    }

    /** The years the person named that this college has no batch for. */
    public static List<Integer> missing(Scope s, CollegeRef ref) {
        return s.years().stream().filter(y -> !ref.batches().contains(y)).toList();
    }

    static String list(List<Integer> batches) {
        if (batches.isEmpty()) return "no batches yet";
        List<String> s = batches.stream().sorted().map(String::valueOf).toList();
        return (s.size() == 1 ? "batch " : "batches ") + (s.size() == 1 ? s.get(0) : String.join(", ", s.subList(0, s.size() - 1)) + " and " + s.get(s.size() - 1));
    }

    @SuppressWarnings("unused")
    private static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
