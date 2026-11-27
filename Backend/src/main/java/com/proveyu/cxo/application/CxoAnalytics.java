package com.proveyu.cxo.application;

import com.proveyu.cxo.domain.CxoModel.Benchmark;
import com.proveyu.cxo.domain.CxoModel.BenchmarkRow;
import com.proveyu.cxo.domain.CxoModel.Change;
import com.proveyu.cxo.domain.CxoModel.ChangeReport;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Forecast;
import com.proveyu.cxo.domain.CxoModel.ForecastPoint;
import com.proveyu.cxo.domain.CxoModel.NameCount;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Views that need more than one dashboard widget: what changed since the last period, a rough projection of the placement season,
 * and the placement figures laid out for NIRF / NAAC. Everything is read from the college's own records, scoped by the signed-in
 * college, and every number is worked out from the same tables the dashboards use.
 */
@Service
@RequiredArgsConstructor
public class CxoAnalytics {

    private static final String PLACED = "('ACCEPTED','JOINED')";

    private final QueryEngine engine;
    private final MetricCatalog catalog;
    private final JdbcTemplate jdbc;

    // ------------------------------------------------------------------ what changed

    /** Metric, label, is "more" good news? */
    private static final List<Object[]> TRACKED = List.of(
            new Object[]{"new_placements", "Students placed", true}, new Object[]{"offers", "Offers made", true},
            new Object[]{"applications", "Applications", true}, new Object[]{"drives", "Drives held", true},
            new Object[]{"interviews", "Interviews", true}, new Object[]{"avg_ctc", "Average CTC (LPA)", true},
            new Object[]{"highest_ctc", "Highest CTC (LPA)", true}, new Object[]{"round_results", "Round results", true},
            new Object[]{"mock_test_attempts", "Mock test attempts", true}, new Object[]{"ai_interviews", "AI interviews", true},
            new Object[]{"code_submissions", "Code ground submissions", true}, new Object[]{"code_active_coders", "Students practising code", true},
            new Object[]{"offcampus_applications", "Off-campus applications", true}, new Object[]{"announcements", "Announcements sent", true},
            new Object[]{"proctoring_flags", "Proctoring flags", false});

    public ChangeReport changes(UUID collegeId, int days) {
        int n = Math.max(7, Math.min(days, 180));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(n - 1L), prevTo = from.minusDays(1), prevFrom = prevTo.minusDays(n - 1L);
        List<Change> changes = new ArrayList<>();
        for (Object[] t : TRACKED) {
            String key = (String) t[0];
            if (catalog.find(key).isEmpty()) continue;
            Double cur = window(key, from, today, collegeId), prev = window(key, prevFrom, prevTo, collegeId);
            changes.add(new Change(key, (String) t[1], catalog.find(key).get().unit(), cur, prev, (boolean) t[2]));
        }
        List<NameCount> companies = new ArrayList<>();
        WidgetSpec byCompany = new WidgetSpec("c", "Offers by company", WidgetType.BAR, "offers", Dimension.COMPANY, null, null, from, today, 5, "md");
        WidgetData d = engine.run(byCompany, collegeId);
        for (int i = 0; i < d.labels().size(); i++) companies.add(new NameCount(d.labels().get(i), d.values().get(i)));
        return new ChangeReport(n, from.toString(), today.toString(), prevFrom.toString(), prevTo.toString(), changes, companies, highlights(changes, n));
    }

    private Double window(String metric, LocalDate from, LocalDate to, UUID collegeId) {
        return engine.value(new WidgetSpec("w", metric, WidgetType.KPI, metric, Dimension.NONE, null, null, from, to, null, "sm"), collegeId);
    }

    /** Plain sentences for the biggest movements (counts of at least 3 either way, so one-offs do not show as news). */
    static List<String> highlights(List<Change> changes, int days) {
        List<String> out = new ArrayList<>();
        List<Change> movers = changes.stream().filter(c -> c.current() != null && c.previous() != null && c.unit() == Unit.COUNT
                && Math.abs(c.current() - c.previous()) >= 3).sorted(Comparator.comparingDouble((Change c) -> -Math.abs(c.current() - c.previous()))).limit(4).toList();
        for (Change c : movers) {
            double diff = c.current() - c.previous();
            out.add(c.label() + ": " + fmt(c.current()) + " in the last " + days + " days, " + (diff > 0 ? "up " : "down ") + fmt(Math.abs(diff)) + " from " + fmt(c.previous()) + ".");
        }
        for (Change c : changes) {
            if (c.unit() == Unit.LPA && c.current() != null && c.previous() != null && !c.current().equals(c.previous())) {
                out.add(c.label() + ": " + fmt(c.current()) + " now, " + fmt(c.previous()) + " in the period before.");
                break;
            }
        }
        if (out.isEmpty()) out.add("No big movement: the numbers are close to the " + days + " days before.");
        return out;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ forecast

    /** @param batch the batch to project (null: this year's, else the next one on record) */
    public Forecast forecast(UUID collegeId, Integer batch, LocalDate until) {
        String c = collegeId.toString();
        LocalDate today = LocalDate.now();
        int year = batch != null ? batch : defaultBatch(c, today.getYear());
        LocalDate end = until != null && until.isAfter(today) ? until : LocalDate.of(today.getYear(), 12, 31);
        if (!end.isAfter(today)) end = today.plusMonths(1);
        int eligible = count("select count(*) from college_students where college_id = ? and batch_year = ? and deleted_at is null"
                + " and status not in ('DEBARRED','OPTED_OUT')", c, year);
        List<LocalDate> firstDates = firstPlacementDates(c, year);
        int placed = firstDates.size();
        int r30 = within(firstDates, today.minusDays(29), today), r60 = within(firstDates, today.minusDays(59), today.minusDays(30)),
                r90 = within(firstDates, today.minusDays(89), today.minusDays(60));
        double low = Math.min(r30, Math.min(r60, r90)), high = Math.max(r30, Math.max(r60, r90)), base = (r30 + r60 + r90) / 3.0;
        double months = java.time.temporal.ChronoUnit.DAYS.between(today, end) / 30.4;
        int remaining = Math.max(0, eligible - placed);
        int pLow = placed + (int) Math.min(remaining, Math.round(low * months)), pBase = placed + (int) Math.min(remaining, Math.round(base * months)),
                pHigh = placed + (int) Math.min(remaining, Math.round(high * months));
        String confidence = r30 + r60 + r90 >= 12 ? "OK" : "LOW";

        List<ForecastPoint> series = new ArrayList<>();
        YearMonth first = firstDates.stream().min(LocalDate::compareTo).map(YearMonth::from).orElse(YearMonth.from(today));
        for (YearMonth m = first; !m.isAfter(YearMonth.from(today)); m = m.plusMonths(1)) {
            YearMonth mm = m;
            series.add(new ForecastPoint(m.toString(), (int) firstDates.stream().filter(d -> !YearMonth.from(d).isAfter(mm)).count(), null, null, null));
        }
        int step = 1;
        for (YearMonth m = YearMonth.from(today).plusMonths(1); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1), step++) {
            double upto = Math.min(months, step);
            series.add(new ForecastPoint(m.toString(), null, placed + (int) Math.min(remaining, Math.round(low * upto)),
                    placed + (int) Math.min(remaining, Math.round(base * upto)), placed + (int) Math.min(remaining, Math.round(high * upto))));
        }

        List<String> notes = new ArrayList<>();
        notes.add("An estimate, not a promise: it assumes the pace of the last 3 months (" + r90 + ", " + r60 + " and " + r30 + " students placed in each 30 days, oldest first) continues. Campus hiring is seasonal.");
        if (confidence.equals("LOW")) notes.add("Few students were placed recently, so this estimate is weak.");
        if (eligible == 0) notes.add("There are no eligible students recorded for batch " + year + ".");
        return new Forecast(year, today.toString(), end.toString(), eligible, placed, pct(placed, eligible), r30, r60, r90, round1(low), round1(base), round1(high),
                pLow, pBase, pHigh, pct(pBase, eligible), confidence, series, lastYear(c, year - 1, today, eligible > 0), notes);
    }

    private String lastYear(String c, int prev, LocalDate today, boolean any) {
        int eligible = count("select count(*) from college_students where college_id = ? and batch_year = ? and deleted_at is null and status not in ('DEBARRED','OPTED_OUT')", c, prev);
        if (!any || eligible == 0) return null;
        List<LocalDate> dates = firstPlacementDates(c, prev);
        LocalDate then = today.minusYears(1);
        int atThen = (int) dates.stream().filter(d -> !d.isAfter(then)).count();
        return "Batch " + prev + " had " + atThen + " of " + eligible + " placed (" + fmt(pct(atThen, eligible)) + "%) around this date last year and ended at "
                + dates.size() + " (" + fmt(pct(dates.size(), eligible)) + "%).";
    }

    private int defaultBatch(String c, int year) {
        List<Integer> years = jdbc.queryForList("select distinct batch_year from college_students where college_id = ? and deleted_at is null and batch_year is not null order by batch_year", Integer.class, c);
        return years.stream().filter(y -> y >= year).findFirst().orElse(years.isEmpty() ? year : years.get(years.size() - 1));
    }

    private List<LocalDate> firstPlacementDates(String c, int batch) {
        return jdbc.query("select min(o.offer_date) d from college_offers o join college_students s on s.id = o.student_id and s.deleted_at is null"
                + " where o.college_id = ? and s.batch_year = ? and o.status in " + PLACED + " group by o.student_id",
                (rs, i) -> rs.getDate("d") == null ? null : rs.getDate("d").toLocalDate(), c, batch).stream().filter(java.util.Objects::nonNull).toList();
    }

    private static int within(List<LocalDate> dates, LocalDate from, LocalDate to) {
        return (int) dates.stream().filter(d -> !d.isBefore(from) && !d.isAfter(to)).count();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static double pct(int a, int b) {
        return b == 0 ? 0 : Math.round(1000.0 * a / b) / 10.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    // ------------------------------------------------------------------ NIRF / NAAC

    /** The last three batches up to {@code year}, side by side, the way the accreditation forms ask for them. */
    public Benchmark benchmark(UUID collegeId, Integer yearOrNull, List<String> branches) {
        String c = collegeId.toString();
        int year = yearOrNull != null ? yearOrNull : defaultBatch(c, LocalDate.now().getYear() - 1);
        List<Integer> years = List.of(year - 2, year - 1, year);
        List<Stats> st = years.stream().map(y -> stats(c, y, null)).toList();
        List<BenchmarkRow> rows = new ArrayList<>();
        rows.add(row("Students in the graduating batch", st, s -> String.valueOf(s.students), "All students on the roll for the batch (not deleted)"));
        rows.add(row("Eligible for placement", st, s -> String.valueOf(s.eligible), "Excludes students debarred or who opted out"));
        rows.add(row("Students placed", st, s -> String.valueOf(s.placed()), "Students with an accepted or joined offer"));
        rows.add(row("Placed, % of eligible", st, s -> fmt(pct(s.placed(), s.eligible)) + "%", null));
        rows.add(row("Median package (LPA)", st, s -> num(CxoMath.median(s.ctc)), "Median of each placed student's highest accepted package"));
        rows.add(row("Average package (LPA)", st, s -> num(CxoMath.mean(s.ctc)), null));
        rows.add(row("Highest package (LPA)", st, s -> num(s.ctc.stream().max(Double::compare).orElse(null)), null));
        rows.add(row("Lowest package (LPA)", st, s -> num(s.ctc.stream().min(Double::compare).orElse(null)), null));
        rows.add(row("Offers made", st, s -> String.valueOf(s.offers), "Offered, accepted or joined"));
        rows.add(row("Companies that made offers", st, s -> String.valueOf(s.companies), null));
        rows.add(row("Students with more than one offer", st, s -> String.valueOf(s.multi), null));
        rows.add(row("Higher studies", st, s -> "Not recorded", "ProveYu does not record progression to higher studies: fill this from your own records"));
        rows.add(row("Entrepreneurship", st, s -> "Not recorded", "Not recorded in ProveYu"));
        List<String> header = List.of("Branch", "Students", "Eligible", "Placed", "Placed %", "Median (LPA)", "Highest (LPA)");
        List<List<String>> table = new ArrayList<>();
        for (String b : branches) {
            Stats s = stats(c, year, b);
            if (s.students == 0) continue;
            table.add(List.of(b, String.valueOf(s.students), String.valueOf(s.eligible), String.valueOf(s.placed()), fmt(pct(s.placed(), s.eligible)) + "%",
                    num(CxoMath.median(s.ctc)), num(s.ctc.stream().max(Double::compare).orElse(null))));
        }
        List<String> notes = List.of("Figures are read from this college's ProveYu records for batch " + year + " and the two batches before it.",
                "Check each definition against the current NIRF / NAAC format before filing: those forms define terms (for example, who counts as placed) in their own way.",
                "A package is the cost to company (CTC) in lakh per annum, as entered on the offer.");
        return new Benchmark(year, years, rows, header, table, notes);
    }

    private static BenchmarkRow row(String label, List<Stats> st, java.util.function.Function<Stats, String> f, String note) {
        return new BenchmarkRow(label, st.stream().map(f).toList(), note);
    }

    private static String num(Double v) {
        return v == null ? "–" : fmt(v);
    }

    private record Stats(int students, int eligible, int offers, int companies, int multi, List<Double> ctc, int placedCount) {
        int placed() { return placedCount; }
    }

    private Stats stats(String c, int batch, String branch) {
        String b = branch == null ? "" : " and s.branch_id = (select id from college_branches where college_id = s.college_id and code = ?)";
        Object[] base = branch == null ? new Object[]{c, batch} : new Object[]{c, batch, branch};
        int students = count("select count(*) from college_students s where s.college_id = ? and s.batch_year = ? and s.deleted_at is null" + b, base);
        int eligible = count("select count(*) from college_students s where s.college_id = ? and s.batch_year = ? and s.deleted_at is null and s.status not in ('DEBARRED','OPTED_OUT')" + b, base);
        Object[] o = new Object[base.length + 0];
        System.arraycopy(base, 0, o, 0, base.length);
        List<Object[]> offers = jdbc.query("select o.student_id, o.company_id, o.status, o.ctc_lpa from college_offers o join college_students s on s.id = o.student_id"
                + " and s.deleted_at is null where o.college_id = ? and s.batch_year = ? and o.status in ('OFFERED','ACCEPTED','JOINED')" + b,
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4) == null ? null : rs.getBigDecimal(4).doubleValue()}, o);
        Map<String, Integer> perStudent = new java.util.HashMap<>();
        Map<String, Double> best = new java.util.HashMap<>();
        java.util.Set<String> companies = new java.util.HashSet<>();
        java.util.Set<String> placedStudents = new java.util.HashSet<>();
        for (Object[] r : offers) {
            perStudent.merge((String) r[0], 1, Integer::sum);
            if (r[1] != null) companies.add((String) r[1]);
            if ("ACCEPTED".equals(r[2]) || "JOINED".equals(r[2])) {
                placedStudents.add((String) r[0]);
                if (r[3] != null) best.merge((String) r[0], (Double) r[3], Math::max);
            }
        }
        int multi = (int) perStudent.values().stream().filter(n -> n >= 2).count();
        return new Stats(students, eligible, offers.size(), companies.size(), multi, new ArrayList<>(best.values()), placedStudents.size());
    }
}
