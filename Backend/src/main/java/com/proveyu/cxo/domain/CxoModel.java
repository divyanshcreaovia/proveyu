package com.proveyu.cxo.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Types of the CXO module: the dashboard spec the AI fills in, widget data, the metric catalog and the request bodies. */
public final class CxoModel {
    private CxoModel() {}

    /** How a widget is drawn. KPI = one number; LINE / AREA need the MONTH dimension. */
    public enum WidgetType { KPI, BAR, COLUMN, LINE, AREA, DONUT, TABLE }

    /** What a widget is grouped by. NONE = a single number. */
    public enum Dimension { NONE, BRANCH, BATCH, MONTH, COMPANY, STATUS, TYPE, TIER, CTC_BAND, TEST, DIFFICULTY, STAGE, ROUND, STUDENT, ANNOUNCEMENT, JOB, YEAR, LANGUAGE, GENDER, CGPA_BAND }

    public enum Unit { COUNT, PERCENT, LPA, SCORE, CGPA }

    // ------------------------------------------------------------------ dashboard spec (what the AI returns, what is saved)

    /**
     * One widget. Filters are optional; the college is never part of the spec (it always comes from the signed-in account).
     *
     * @param size sm (KPI tile), md (half row), lg (full row)
     */
    public record WidgetSpec(String id, String title, WidgetType type, String metric, Dimension dimension, Integer batchYear,
                             String branchCode, LocalDate from, LocalDate to, Integer limit, String size, List<Integer> batches) {
        /** Without a set of years (the usual widget). */
        public WidgetSpec(String id, String title, WidgetType type, String metric, Dimension dimension, Integer batchYear,
                          String branchCode, LocalDate from, LocalDate to, Integer limit, String size) {
            this(id, title, type, metric, dimension, batchYear, branchCode, from, to, limit, size, null);
        }

        public WidgetSpec withBatches(List<Integer> years) {
            return new WidgetSpec(id, title, type, metric, dimension, batchYear, branchCode, from, to, limit, size, years);
        }
    }

    public record DashboardSpec(@Size(max = 200) String title, @Size(max = 500) String summary, @Valid @Size(max = 12) List<WidgetSpec> widgets) {}

    // ------------------------------------------------------------------ results

    /** Data of one widget: a single value (KPI) or labels with values (charts and tables). */
    /**
     * Numbers for one widget. A KPI filtered to a batch or a date range also carries the same number for the previous batch or the
     * same period a year earlier ({@code previous}, described by {@code previousLabel}), so the change can be shown.
     */
    public record WidgetData(String widgetId, Unit unit, Double value, List<String> labels, List<Double> values, Double previous,
                             String previousLabel) {
        public WidgetData(String widgetId, Unit unit, Double value, List<String> labels, List<Double> values) {
            this(widgetId, unit, value, labels, values, null, null);
        }
    }

    public record Insight(String headline, List<String> bullets) {}

    // ------------------------------------------------------------------ drill-down, changes, forecast, benchmark, sharing

    /** One student behind a number. */
    public record DrillRow(String name, String roll, String branch, Integer batch, Double cgpa, String status, boolean placed) {}

    /** @param truncated more students exist than are listed; @param note why there is no list (when there is none) */
    public record Drill(List<DrillRow> rows, boolean truncated, String note) {}

    public record DrillRequest(@NotNull @Valid WidgetSpec widget, @Size(max = 200) String label) {}

    /** One number compared with the period before it. */
    public record Change(String key, String label, Unit unit, Double current, Double previous, boolean higherIsBetter) {}

    public record NameCount(String name, double count) {}

    public record ChangeReport(int days, String from, String to, String previousFrom, String previousTo, List<Change> changes,
                               List<NameCount> topCompanies, List<String> highlights) {}

    public record ForecastPoint(String month, Integer placed, Integer low, Integer base, Integer high) {}

    /**
     * A rough projection of one batch's placements to a date. {@code confidence} is LOW when too few placements happened recently;
     * {@code lastYear} says where the batch before it stood on the same date and where it ended.
     */
    public record Forecast(int batch, String asOf, String until, int eligible, int placed, double placedPercent, int recent30, int recent60, int recent90,
                           double perMonthLow, double perMonthBase, double perMonthHigh, int projectedLow, int projectedBase, int projectedHigh,
                           double projectedBasePercent, String confidence, List<ForecastPoint> series, String lastYear, List<String> notes) {}

    public record BenchmarkRow(String label, List<String> values, String note) {}

    public record Benchmark(int year, List<Integer> years, List<BenchmarkRow> rows, List<String> branchHeader, List<List<String>> branches,
                            List<String> notes) {}

    public record ShareRequest(@NotNull UUID dashboardId, Integer days) {}

    public record ShareLink(UUID id, UUID dashboardId, String token, String path, Instant expiresAt, Instant createdAt, int views, boolean revoked) {}

    /** What an outside reader sees: the board and its fresh numbers, never student names. */
    public record SharedBoard(String title, String college, String sharedBy, DashboardSpec spec, List<WidgetData> data, Insight insight,
                              Instant expiresAt, Instant generatedAt) {}

    /** Progress step shown while a dashboard is being built. */
    public record Status(String step, String message) {}

    /** The finished stream: the final spec (to save or refine) and how it was planned. */
    /** @param cached true when the plan came from the AI answer cache */
    /** @param notices plain-language notes about the data (a year that is not on record, nothing recorded yet), shown above the board */
    public record Done(DashboardSpec spec, String source, boolean cached, long durationMs, List<String> notices) {}

    // ------------------------------------------------------------------ catalog / profile

    public record MetricInfo(String key, String label, String description, Unit unit, List<Dimension> dimensions, boolean drillable) {}

    public record Catalog(List<MetricInfo> metrics, List<String> branches, List<Integer> batches, List<String> suggestions, boolean aiEnabled) {}

    public record Me(UUID userId, String fullName, String email, String designation, UUID collegeId, String collegeName) {}

    // ------------------------------------------------------------------ saved dashboards

    public record DashboardSummary(UUID id, String title, String prompt, int widgets, boolean pinned, Instant updatedAt) {}

    public record DashboardDetail(UUID id, String title, String prompt, DashboardSpec spec, boolean pinned, Instant updatedAt) {}

    // ------------------------------------------------------------------ requests

    /** @param current the dashboard on screen, sent with a follow-up such as "make it monthly" (optional) */
    public record GenerateRequest(@NotBlank @Size(max = 1000) String prompt, @Valid DashboardSpec current) {}

    public record RunRequest(@NotNull @Valid DashboardSpec spec) {}

    /** The AI summary of a dashboard's current numbers (cached per numbers, so it is only re-written when they change). */
    public record SummaryRequest(@Size(max = 2000) String prompt, @NotNull @Valid DashboardSpec spec) {}

    public record SaveRequest(@NotBlank @Size(max = 200) String title, @Size(max = 2000) String prompt, @NotNull @Valid DashboardSpec spec,
                              Boolean pinned) {}

    public record PinRequest(boolean pinned) {}

    // ------------------------------------------------------------------ platform admin: CXO accounts

    public record CxoUserRow(UUID userId, UUID memberId, String fullName, String email, String designation, UUID collegeId,
                             String collegeName, String status, boolean active, Instant lastLoginAt, Instant createdAt) {}

    public record CreateCxoRequest(@NotNull UUID collegeId, @NotBlank @Size(max = 150) String fullName, @NotBlank @Email @Size(max = 190) String email,
                                   @Size(max = 150) String designation, @NotBlank @Size(min = 8, max = 100) String password) {}
}
