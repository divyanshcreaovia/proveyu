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
    public enum Dimension { NONE, BRANCH, BATCH, MONTH, COMPANY, STATUS, TYPE, TIER, CTC_BAND, TEST, DIFFICULTY, STAGE, ROUND, STUDENT, ANNOUNCEMENT, JOB }

    public enum Unit { COUNT, PERCENT, LPA, SCORE, CGPA }

    // ------------------------------------------------------------------ dashboard spec (what the AI returns, what is saved)

    /**
     * One widget. Filters are optional; the college is never part of the spec (it always comes from the signed-in account).
     *
     * @param size sm (KPI tile), md (half row), lg (full row)
     */
    public record WidgetSpec(String id, String title, WidgetType type, String metric, Dimension dimension, Integer batchYear,
                             String branchCode, LocalDate from, LocalDate to, Integer limit, String size) {}

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

    /** Progress step shown while a dashboard is being built. */
    public record Status(String step, String message) {}

    /** The finished stream: the final spec (to save or refine) and how it was planned. */
    /** @param cached true when the plan came from the AI answer cache */
    /** @param notices plain-language notes about the data (a year that is not on record, nothing recorded yet), shown above the board */
    public record Done(DashboardSpec spec, String source, boolean cached, long durationMs, List<String> notices) {}

    // ------------------------------------------------------------------ catalog / profile

    public record MetricInfo(String key, String label, String description, Unit unit, List<Dimension> dimensions) {}

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
