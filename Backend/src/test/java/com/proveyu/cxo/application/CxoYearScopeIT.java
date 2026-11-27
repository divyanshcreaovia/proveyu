package com.proveyu.cxo.application;

import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.proveyu.cxo.domain.CxoModel.*;
import com.proveyu.cxo.security.CxoContext;
import com.proveyu.shared.error.DomainException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Data integrity of the year questions, against a real database with data we know exactly: one year asked shows that year only, a
 * year with nothing shows nothing, several years show exactly those years, another college's numbers never appear.
 */
class CxoYearScopeIT extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired QueryEngine engine;
    @Autowired DashboardPlanner planner;
    @MockBean CxoAiClient ai;

    UUID college;
    final CollegeRef ref = new CollegeRef(List.of("COMP"), List.of(2025, 2026));

    @BeforeEach
    void data() throws Exception {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty()); // the keyword planner
        college = newCollege();
        UUID other = newCollege();
        UUID comp = branch(college, "COMP");
        UUID acme = company(college, "Acme");
        // batch 2025: a, b   batch 2026: c, d, e
        UUID a = student(college, comp, 2025), b = student(college, comp, 2025);
        UUID c = student(college, comp, 2026), d = student(college, comp, 2026);
        student(college, comp, 2026);
        offer(college, a, acme, "ACCEPTED", 10, "2025-09-01");
        offer(college, b, acme, "ACCEPTED", 6, "2025-10-01");
        offer(college, c, acme, "OFFERED", 8, "2026-02-01");
        offer(college, d, acme, "ACCEPTED", 12, "2026-03-01");
        drive(college, acme, "2025-05-10");
        drive(college, acme, "2025-11-10");
        drive(college, acme, "2026-02-10");
        // another college with a lot of data in the same years: must never show up
        UUID ob = branch(other, "COMP"), oc = company(other, "Other");
        for (int i = 0; i < 4; i++) {
            UUID s = student(other, ob, i % 2 == 0 ? 2025 : 2026);
            offer(other, s, oc, "ACCEPTED", 30, i % 2 == 0 ? "2025-08-01" : "2026-08-01");
            drive(other, oc, "2025-06-01");
        }
    }

    // ------------------------------------------------------------------ helpers

    UUID newCollege() throws Exception {
        List<String> before = jdbc.queryForList("select college_id from college_profiles", String.class);
        registerCollege("ys");
        List<String> after = jdbc.queryForList("select college_id from college_profiles", String.class);
        after.removeAll(before);
        assertThat(after).hasSize(1);
        return UUID.fromString(after.get(0));
    }

    UUID branch(UUID col, String code) {
        List<String> have = jdbc.queryForList("select id from college_branches where college_id = ? and code = ?", String.class, col.toString(), code);
        if (!have.isEmpty()) return UUID.fromString(have.get(0)); // registration already created the standard branches
        UUID id = UUID.randomUUID();
        jdbc.update("insert into college_branches (id, college_id, code, name) values (?,?,?,?)", id.toString(), col.toString(), code, code + " dept");
        return id;
    }

    UUID company(UUID col, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into college_companies (id, college_id, name) values (?,?,?)", id.toString(), col.toString(), name);
        return id;
    }

    UUID student(UUID col, UUID branch, int batch) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into college_students (id, college_id, branch_id, roll_number, first_name, email, batch_year) values (?,?,?,?,?,?,?)",
                id.toString(), col.toString(), branch.toString(), "R" + uid(), "S" + uid(), uid() + "@x.in", batch);
        return id;
    }

    void offer(UUID col, UUID student, UUID comp, String status, double ctc, String date) {
        jdbc.update("insert into college_offers (id, college_id, student_id, company_id, role_title, ctc_lpa, status, offer_date) values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), col.toString(), student.toString(), comp.toString(), "SDE", ctc, status, date);
    }

    void drive(UUID col, UUID comp, String date) {
        jdbc.update("insert into college_drives (id, college_id, company_id, title, role_title, drive_date) values (?,?,?,?,?,?)",
                UUID.randomUUID().toString(), col.toString(), comp.toString(), "Drive", "SDE", date);
    }

    WidgetSpec kpi(String metric, Integer batch, LocalDate from, LocalDate to) {
        return new WidgetSpec("w", "T", WidgetType.KPI, metric, Dimension.NONE, batch, null, from, to, null, "sm");
    }

    WidgetSpec chart(String metric, Dimension dim, Integer batch, List<Integer> batches) {
        return new WidgetSpec("w", "T", WidgetType.COLUMN, metric, dim, batch, null, null, null, null, "md", batches);
    }

    // ------------------------------------------------------------------ one year

    @Test
    void oneBatchShowsOnlyThatBatch() {
        assertThat(engine.run(kpi("offers", 2025, null, null), college).value()).isEqualTo(2.0);
        assertThat(engine.run(kpi("offers", 2026, null, null), college).value()).isEqualTo(2.0);
        assertThat(engine.run(kpi("students_total", 2025, null, null), college).value()).isEqualTo(2.0);
        assertThat(engine.run(kpi("students_total", 2026, null, null), college).value()).isEqualTo(3.0);
        assertThat(engine.run(kpi("avg_ctc", 2025, null, null), college).value()).isEqualTo(8.0);
        assertThat(engine.run(kpi("highest_ctc", 2026, null, null), college).value()).isEqualTo(12.0);
        assertThat(engine.run(kpi("placed_students", 2025, null, null), college).value()).isEqualTo(2.0);
        assertThat(engine.run(kpi("placement_rate", 2026, null, null), college).value()).isEqualTo(33.3); // 1 of 3
        assertThat(engine.run(kpi("students_total", null, null, null), college).value()).isEqualTo(5.0); // no year: everything
    }

    @Test
    void aBatchWithNoStudentsShowsNothing() {
        for (String m : List.of("students_total", "offers", "placement_rate", "avg_ctc", "applications", "mock_test_attempts")) {
            WidgetData d = engine.run(kpi(m, 2024, null, null), college);
            assertThat(d.value()).as(m).isNull();
        }
        WidgetData chart = engine.run(chart("offers", Dimension.COMPANY, 2024, null), college);
        assertThat(chart.labels()).isEmpty();
        assertThat(engine.run(chart("placement_funnel", Dimension.STAGE, 2024, null), college).labels()).isEmpty();
    }

    @Test
    void aDateOnlyMetricUsesTheCalendarYear() {
        assertThat(engine.run(kpi("drives", null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), college).value()).isEqualTo(2.0);
        assertThat(engine.run(kpi("drives", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), college).value()).isEqualTo(1.0);
        assertThat(engine.run(kpi("drives", null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)), college).value()).isEqualTo(0.0);
    }

    @Test
    void aKpiShowsTheChangeFromThePreviousBatchOrYear() {
        WidgetData d = engine.run(kpi("offers", 2026, null, null), college);
        assertThat(d.previous()).isEqualTo(2.0);
        assertThat(d.previousLabel()).isEqualTo("batch 2025");
        assertThat(engine.run(kpi("offers", 2025, null, null), college).previous()).isNull(); // no batch 2024: nothing to compare with
        WidgetData drives = engine.run(kpi("drives", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), college);
        assertThat(drives.value()).isEqualTo(1.0);
        assertThat(drives.previous()).isEqualTo(2.0);
        assertThat(drives.previousLabel()).isEqualTo("2025");
    }

    @Test
    void aPastBatchTrendCoversItsOwnYears() {
        WidgetData m = engine.run(chart("offers", Dimension.MONTH, 2025, null), college);
        assertThat(m.labels()).contains("2025-09", "2025-10").first().isEqualTo("2024-01");
        assertThat(m.values().stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(2.0);
        assertThat(m.labels().get(m.labels().indexOf("2025-09"))).isEqualTo("2025-09");
        assertThat(m.values().get(m.labels().indexOf("2025-09"))).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------ several years

    @Test
    void namedYearsAreExactlyTheYearsShown() {
        WidgetData d = engine.run(chart("offers", Dimension.BATCH, null, List.of(2024, 2026)), college);
        assertThat(d.labels()).containsExactly("2026"); // 2024 has no batch: not drawn
        assertThat(d.values()).containsExactly(2.0);
        WidgetData both = engine.run(chart("offers", Dimension.BATCH, null, List.of(2025, 2026)), college);
        assertThat(both.labels()).containsExactly("2025", "2026");
        assertThat(both.values()).containsExactly(2.0, 2.0);
        WidgetData none = engine.run(chart("offers", Dimension.BATCH, null, List.of(2023, 2024)), college);
        assertThat(none.labels()).isEmpty();
        WidgetData all = engine.run(chart("offers", Dimension.BATCH, null, null), college);
        assertThat(all.labels()).containsExactly("2025", "2026");
    }

    @Test
    void ratesAndAveragesByBatchMatchTheSingleYearNumbers() {
        for (String metric : List.of("placement_rate", "avg_ctc", "highest_ctc", "students_total", "placed_students")) {
            WidgetData by = engine.run(chart(metric, Dimension.BATCH, null, List.of(2025, 2026)), college);
            for (int i = 0; i < by.labels().size(); i++) {
                Double single = engine.run(kpi(metric, Integer.parseInt(by.labels().get(i)), null, null), college).value();
                assertThat(by.values().get(i)).as(metric + " " + by.labels().get(i)).isEqualTo(single);
            }
        }
    }

    @Test
    void dateOnlyMetricsGoByCalendarYear() {
        WidgetData d = engine.run(chart("drives", Dimension.YEAR, null, List.of(2024, 2025, 2026)), college);
        assertThat(d.labels()).containsExactly("2025", "2026");
        assertThat(d.values()).containsExactly(2.0, 1.0);
    }

    // ------------------------------------------------------------------ from the question to the numbers

    private List<WidgetData> ask(String prompt) {
        DashboardSpec spec = planner.plan(college, prompt, null, ref).spec();
        return spec.widgets().stream().map(w -> engine.run(w, college)).toList();
    }

    @Test
    void aYearWithNoBatchAnswersWithNoNumbers() {
        DashboardSpec spec = planner.plan(college, "placement overview 2024", null, ref).spec();
        for (WidgetSpec w : spec.widgets()) {
            WidgetData d = engine.run(w, college);
            if (w.metric().equals("drives") || w.metric().equals("interviews")) continue; // dated: a count of 0 for the year
            assertThat(d.value()).as(w.title()).isNull();
            assertThat(d.labels()).as(w.title()).isEmpty();
        }
    }

    @Test
    void aQuestionAboutOneYearNeverShowsAnotherYear() {
        DashboardSpec spec = planner.plan(college, "placement overview 2025", null, ref).spec();
        assertThat(spec.widgets()).allMatch(w -> w.batches() == null);
        WidgetData students = spec.widgets().stream().filter(w -> w.metric().equals("students_total")).map(w -> engine.run(w, college)).findFirst().orElseThrow();
        assertThat(students.value()).isEqualTo(2.0);
        WidgetData offers = spec.widgets().stream().filter(w -> w.metric().equals("offers") && w.dimension() == Dimension.MONTH).map(w -> engine.run(w, college)).findFirst().orElseThrow();
        assertThat(offers.values().stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(2.0);
        assertThat(offers.labels()).allMatch(l -> l.compareTo("2024-01") >= 0 && l.compareTo("2025-12") <= 0);
    }

    @Test
    void aComparisonShowsOnlyTheNamedYears() {
        DashboardSpec spec = planner.plan(college, "compare placement 2024 with 2026", null, ref).spec();
        assertThat(spec.widgets()).isNotEmpty().allMatch(w -> w.batches() != null && w.batches().equals(List.of(2024, 2026)));
        for (WidgetSpec w : spec.widgets()) {
            WidgetData d = engine.run(w, college);
            assertThat(d.labels()).as(w.title()).doesNotContain("2025", "2024");
        }
    }

    @Test
    void allYearsShowsEveryYearOnRecord() {
        DashboardSpec spec = planner.plan(college, "compare placement across all years", null, ref).spec();
        WidgetSpec offers = spec.widgets().stream().filter(w -> w.metric().equals("offers")).findFirst().orElseThrow();
        WidgetData d = engine.run(offers, college);
        assertThat(d.labels()).containsExactly("2025", "2026");
    }

    @Test
    void anotherCollegesNumbersNeverAppear() {
        assertThat(ask("placement overview")).isNotEmpty();
        assertThat(engine.run(kpi("offers", null, null, null), college).value()).isEqualTo(4.0);
        assertThat(engine.run(kpi("highest_ctc", null, null, null), college).value()).isEqualTo(12.0); // the other college has 30
        assertThat(engine.run(kpi("drives", null, null, null), college).value()).isEqualTo(3.0);
        assertThat(engine.run(chart("drives", Dimension.YEAR, null, List.of(2025)), college).values()).containsExactly(2.0);
    }

    @Autowired MetricCatalog catalog;
    @Autowired CxoAnalytics analytics;
    @Autowired CxoShareService shares;
    @Autowired CxoDashboardService service;
    @Autowired ReportExporter exporter;

    String userId() {
        return jdbc.queryForObject("select id from users limit 1", String.class);
    }

    CxoContext ctx(UUID col) {
        return new CxoContext(col, UUID.fromString(userId()), UUID.randomUUID(), "Dr Test", "t@x.in", "Principal", "Test College");
    }

    UUID savedDashboard(UUID col, DashboardSpec spec) throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into cxo_dashboards (id, college_id, user_id, title, spec_json) values (?,?,?,?,?)", id.toString(), col.toString(), userId(), "Board", json.writeValueAsString(spec));
        return id;
    }

    // ------------------------------------------------------------------ median, new placements, drill-down

    @Test
    void medianCtcIsTheMiddleValue() {
        assertThat(engine.run(kpi("median_ctc", null, null, null), college).value()).isEqualTo(9.0);   // 6, 8, 10, 12
        assertThat(engine.run(kpi("median_ctc", 2025, null, null), college).value()).isEqualTo(8.0);   // 6, 10
        assertThat(engine.run(kpi("median_ctc", 2026, null, null), college).value()).isEqualTo(10.0);  // 8, 12
        assertThat(engine.run(kpi("median_ctc", 2024, null, null), college).value()).isNull();
        WidgetData by = engine.run(chart("median_ctc", Dimension.BATCH, null, List.of(2025, 2026)), college);
        assertThat(by.labels()).containsExactly("2025", "2026");
        assertThat(by.values()).containsExactly(8.0, 10.0);
    }

    @Test
    void newPlacementsCountsStudentsByTheirFirstPlacementDate() {
        assertThat(engine.run(kpi("new_placements", 2025, null, null), college).value()).isEqualTo(2.0);
        assertThat(engine.run(kpi("new_placements", null, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)), college).value()).isEqualTo(1.0);
        assertThat(engine.run(kpi("new_placements", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), college).value()).isEqualTo(1.0);
    }

    @Test
    void drillListsExactlyTheStudentsBehindTheNumber() {
        Drill acme = engine.drill(chart("offers", Dimension.COMPANY, 2025, null), "Acme", college, 200);
        assertThat(acme.rows()).hasSize(2).allMatch(r -> r.batch() == 2025 && r.placed());
        // the list size equals the chart's own number for count metrics over students
        WidgetData byBranch = engine.run(chart("students_total", Dimension.BRANCH, null, null), college);
        assertThat(engine.drill(chart("students_total", Dimension.BRANCH, null, null), "COMP", college, 200).rows()).hasSize(byBranch.values().get(byBranch.labels().indexOf("COMP")).intValue());
        assertThat(engine.drill(chart("placed_students", Dimension.BATCH, null, null), "2026", college, 200).rows()).hasSize(1);
        assertThat(engine.drill(kpi("placed_students", 2026, null, null), null, college, 200).rows()).hasSize(1);
        assertThat(engine.drill(kpi("unplaced_students", null, null, null), null, college, 200).rows()).hasSize(2).noneMatch(r -> r.placed());
        // another batch's students never appear; a batch with no students is empty; a bar nobody is in is empty
        assertThat(engine.drill(kpi("students_total", 2024, null, null), null, college, 200).rows()).isEmpty();
        assertThat(engine.drill(chart("offers", Dimension.COMPANY, 2025, null), "Nobody Ltd", college, 200).rows()).isEmpty();
        assertThat(engine.drill(kpi("students_total", 2025, null, null), null, college, 1).truncated()).isTrue();
        // a number that is not about students says so
        Drill drives = engine.drill(kpi("drives", null, null, null), null, college, 200);
        assertThat(drives.rows()).isEmpty();
        assertThat(drives.note()).contains("not counted over students");
    }

    // ------------------------------------------------------------------ what changed, forecast, benchmark

    @Test
    void whatChangedComparesTwoPeriods() {
        UUID acme = UUID.fromString(jdbc.queryForObject("select id from college_companies where college_id = ? limit 1", String.class, college.toString()));
        UUID comp = branch(college, "COMP");
        UUID fresh = student(college, comp, 2026);
        offer(college, fresh, acme, "ACCEPTED", 7, LocalDate.now().toString());
        ChangeReport r = analytics.changes(college, 30);
        assertThat(r.days()).isEqualTo(30);
        Change offers = r.changes().stream().filter(c -> c.key().equals("offers")).findFirst().orElseThrow();
        assertThat(offers.current()).isEqualTo(1.0);
        assertThat(offers.previous()).isEqualTo(0.0);
        assertThat(r.changes().stream().filter(c -> c.key().equals("new_placements")).findFirst().orElseThrow().current()).isEqualTo(1.0);
        assertThat(r.topCompanies()).extracting(NameCount::name).containsExactly("Acme");
        assertThat(r.highlights()).isNotEmpty();
        assertThat(analytics.changes(college, 3).days()).isEqualTo(7); // at least a week
    }

    @Test
    void forecastIsAnEstimateWithItsReasons() {
        Forecast f = analytics.forecast(college, 2026, null);
        assertThat(f.eligible()).isEqualTo(3);
        assertThat(f.placed()).isEqualTo(1);
        assertThat(f.projectedLow()).isBetween(1, 3);
        assertThat(f.projectedBase()).isBetween(f.projectedLow(), f.projectedHigh());
        assertThat(f.projectedHigh()).isLessThanOrEqualTo(3);
        assertThat(f.confidence()).isEqualTo("LOW");
        assertThat(f.series()).isNotEmpty();
        assertThat(f.lastYear()).contains("Batch 2025 had").contains("of 2").contains("ended at 2");
        assertThat(f.notes()).anyMatch(n -> n.contains("estimate"));
        assertThat(analytics.forecast(college, 2024, null).eligible()).isZero(); // no such batch: nothing to project
    }

    @Test
    void benchmarkSetsTheThreeBatchesSideBySide() {
        Benchmark b = analytics.benchmark(college, 2026, List.of("COMP"));
        assertThat(b.years()).containsExactly(2024, 2025, 2026);
        java.util.function.Function<String, List<String>> row = label -> b.rows().stream().filter(r -> r.label().equals(label)).findFirst().orElseThrow().values();
        assertThat(row.apply("Students in the graduating batch")).containsExactly("0", "2", "3");
        assertThat(row.apply("Students placed")).containsExactly("0", "2", "1");
        assertThat(row.apply("Median package (LPA)")).containsExactly("–", "8", "12");
        assertThat(row.apply("Highest package (LPA)")).containsExactly("–", "10", "12");
        assertThat(row.apply("Offers made")).containsExactly("0", "2", "2");
        assertThat(row.apply("Higher studies")).containsOnly("Not recorded");
        assertThat(b.branches()).anyMatch(r -> r.get(0).equals("COMP") && r.get(1).equals("3"));
        assertThat(b.notes()).anyMatch(n -> n.contains("Check each definition"));
    }

    // ------------------------------------------------------------------ share links

    @Test
    void sharedLinkIsReadOnlyFreshAndEndsWhenRevokedOrExpired() throws Exception {
        DashboardSpec spec = new DashboardSpec("Board", null, List.of(
                new WidgetSpec("w1", "Offers", WidgetType.KPI, "offers", Dimension.NONE, 2025, null, null, null, null, "sm"),
                new WidgetSpec("w2", "Unplaced", WidgetType.TABLE, "unplaced_list", Dimension.STUDENT, null, null, null, null, 20, "lg")));
        UUID dash = savedDashboard(college, spec);
        ShareLink link = shares.create(ctx(college), dash, 7);
        assertThat(link.token()).hasSizeGreaterThan(30);
        assertThat(jdbc.queryForObject("select count(*) from cxo_dashboard_shares where token_hash = ?", Integer.class, link.token())).isZero(); // only the hash is kept

        SharedBoard board = shares.open(link.token());
        assertThat(board.spec().widgets()).extracting(WidgetSpec::metric).containsExactly("offers"); // no student names outside
        assertThat(board.data().get(0).value()).isEqualTo(2.0);
        assertThat(board.college()).isNotBlank();
        assertThat(shares.list(ctx(college), dash).get(0).views()).isEqualTo(1);
        assertThat(shares.list(ctx(college), dash).get(0).token()).isNull();                         // the secret is not shown again

        assertThatThrownBy(() -> shares.open("not-a-real-token-not-a-real-token")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> shares.create(ctx(UUID.randomUUID()), dash, 7)).isInstanceOf(DomainException.class); // not another college's board

        jdbc.update("update cxo_dashboard_shares set expires_at = date_sub(utc_timestamp(), interval 1 hour) where id = ?", link.id().toString());
        assertThatThrownBy(() -> shares.open(link.token())).isInstanceOf(DomainException.class);
        ShareLink second = shares.create(ctx(college), dash, 7);
        shares.revoke(ctx(college), second.id());
        assertThatThrownBy(() -> shares.open(second.token())).isInstanceOf(DomainException.class);
    }

    @Test
    void publicLinkNeedsNoLoginButEverythingElseDoes() throws Exception {
        assertThat(get("/api/v1/public/cxo/share/definitely-not-a-valid-token-0000", null).status()).isEqualTo(404);
        assertThat(get("/api/v1/cxo/benchmark", null).status()).isEqualTo(401);
        assertThat(post("/api/v1/cxo/shares", null, java.util.Map.of("dashboardId", UUID.randomUUID().toString())).status()).isEqualTo(401);
        assertThat(get("/api/v1/cxo/changes", registerCollege("tpo")).status()).isEqualTo(403); // a college login is not a leadership login
    }

    // ------------------------------------------------------------------ board report

    @Test
    void boardReportIsARealPdfAndPowerPoint() throws Exception {
        DashboardSpec spec = new DashboardSpec("Placement overview", "summary", List.of(
                new WidgetSpec("w1", "Offers", WidgetType.KPI, "offers", Dimension.NONE, 2025, null, null, null, null, "sm"),
                new WidgetSpec("w2", "Offers by company", WidgetType.BAR, "offers", Dimension.COMPANY, 2025, null, null, null, null, "md"),
                new WidgetSpec("w3", "Offers per month", WidgetType.LINE, "offers", Dimension.MONTH, 2025, null, null, null, null, "md"),
                new WidgetSpec("w4", "Unplaced", WidgetType.TABLE, "unplaced_list", Dimension.STUDENT, null, null, null, null, 20, "lg")));
        byte[] pdf = service.export(ctx(college), new SummaryRequest("placement 2025", spec), false);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(d);
            assertThat(text).contains("Placement overview").contains("Test College").contains("Acme");
            assertThat(text).doesNotContain("Unplaced"); // no student names in a report that gets forwarded
        }
        byte[] pptx = service.export(ctx(college), new SummaryRequest("placement 2025", spec), true);
        try (org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(new java.io.ByteArrayInputStream(pptx))) {
            assertThat(ppt.getSlides().size()).isGreaterThanOrEqualTo(3);
        }
        byte[] bench = service.exportBenchmark(ctx(college), analytics.benchmark(college, 2026, List.of("COMP")), false);
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(bench)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(d)).contains("Students placed").contains("Median package");
        }
    }

    /** Every metric with every dimension it offers, with and without a year, a set of years and a branch, runs without a database error. */
    @Test
    void everyMetricAndDimensionRuns() {
        int ran = 0;
        for (MetricCatalog.Def d : catalog.all()) {
            for (Dimension dim : catalog.dimensions(d)) {
                WidgetType type = dim == Dimension.NONE ? WidgetType.KPI : WidgetType.BAR;
                for (Integer batch : new Integer[]{null, 2025, 2024}) {
                    for (String branch : new String[]{null, "COMP"}) {
                        for (List<Integer> years : java.util.Arrays.asList(null, List.of(2024, 2025, 2026))) {
                            WidgetSpec w = new WidgetSpec("w", "T", type, d.key(), dim, years != null ? null : batch, branch,
                                    dim == Dimension.MONTH || years != null ? null : LocalDate.of(2025, 1, 1),
                                    dim == Dimension.MONTH || years != null ? null : LocalDate.of(2025, 12, 31), null, "md", years);
                            engine.run(w, college);
                            ran++;
                        }
                    }
                }
            }
        }
        assertThat(ran).isGreaterThan(500);
    }

    @Test
    void theNewMetricsAreInTheCatalog() {
        assertThat(catalog.infos()).extracting(m -> m.key()).contains("median_ctc", "new_placements", "code_submissions", "code_accept_rate", "code_active_coders",
                "code_problems_solved", "lowest_ctc", "companies_hired", "avg_cgpa");
    }

    /**
     * The regression set: every question in questions.txt is planned and run against the data we know. The years it names are the only
     * years in the plan, the metrics it should pick are in the plan, and no widget fails.
     */
    @Test
    void theQuestionSetKeepsWorking() throws Exception {
        List<String> lines = new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/cxo/questions.txt"),
                java.nio.charset.StandardCharsets.UTF_8)).lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertThat(lines.size()).isGreaterThanOrEqualTo(100);
        List<String> problems = new java.util.ArrayList<>();
        for (String line : lines) {
            String[] part = line.split(" ;; ");
            String q = part[0].trim(), scope = part[1].trim(), metrics = part[2].trim();
            DashboardSpec spec = planner.plan(college, q, null, ref).spec();
            if (spec.widgets().isEmpty()) { problems.add(q + ": empty plan"); continue; }
            if (!metrics.equals("-") && spec.widgets().stream().noneMatch(w -> List.of(metrics.split(",")).contains(w.metric()))) {
                problems.add(q + ": none of " + metrics + " in " + spec.widgets().stream().map(WidgetSpec::metric).distinct().toList());
            }
            for (WidgetSpec w : spec.widgets()) {
                String at = q + " -> " + w.title() + " [" + w.metric() + "/" + w.dimension() + "]";
                if (scope.equals("-")) {
                    if (w.batchYear() != null || w.batches() != null) problems.add(at + ": a year appeared that was not asked");
                } else if (scope.startsWith("Y")) {
                    int y = Integer.parseInt(scope.substring(1));
                    boolean ok = w.batches() == null && (Integer.valueOf(y).equals(w.batchYear())
                            || (w.batchYear() == null && LocalDate.of(y, 1, 1).equals(w.from()) && LocalDate.of(y, 12, 31).equals(w.to())));
                    if (!ok) problems.add(at + ": not scoped to " + y + " (batch " + w.batchYear() + ", from " + w.from() + ", batches " + w.batches() + ")");
                } else {
                    List<Integer> want = scope.equals("ALL") ? ref.batches() : java.util.Arrays.stream(scope.substring(1).split(",")).map(Integer::valueOf).toList();
                    if (!want.equals(w.batches())) problems.add(at + ": years " + w.batches() + " instead of " + want);
                }
                try {
                    WidgetData d = engine.run(w, college);
                    if (scope.startsWith("Y") && ref.batches().stream().noneMatch(b -> b == Integer.parseInt(scope.substring(1))) && w.batchYear() != null) {
                        if (d.value() != null || !d.labels().isEmpty()) problems.add(at + ": shows numbers for a year with no batch");
                    }
                    if (w.batches() != null && d.labels().stream().anyMatch(l -> !w.batches().contains(Integer.valueOf(l)))) problems.add(at + ": a year outside the ones asked: " + d.labels());
                } catch (Exception e) {
                    problems.add(at + ": failed " + e.getMessage());
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    /** The fifteen questions offered in the Ask AI list: each builds a dashboard, every widget runs, and none shows a year that was not asked. */
    @Test
    void everySuggestedQuestionBuilds() {
        Catalog c = service.catalog(ctx(college));
        assertThat(c.suggestions()).hasSize(15).doesNotHaveDuplicates();
        for (String q : c.suggestions()) {
            DashboardSpec spec = planner.plan(college, q, null, ref).spec();
            assertThat(spec.widgets()).as(q).isNotEmpty();
            for (WidgetSpec w : spec.widgets()) {
                assertThat(w.metric()).as(q).isNotBlank();
                engine.run(w, college); // no database error
            }
            boolean asksYear = q.matches(".*\\b20\\d{2}\\b.*") || q.toLowerCase().contains("this year") || q.toLowerCase().contains("all years");
            if (!asksYear) assertThat(spec.widgets()).as(q).allMatch(w -> w.batchYear() == null && w.batches() == null);
        }
    }
}
