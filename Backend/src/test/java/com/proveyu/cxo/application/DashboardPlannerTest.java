package com.proveyu.cxo.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Planner + sanitizer + query building, without a database or a real model. */
class DashboardPlannerTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MetricCatalog catalog = new MetricCatalog();
    private final SpecSanitizer sanitizer = new SpecSanitizer(catalog);
    private final CollegeRef ref = new CollegeRef(List.of("AIDS", "COMP", "MECH"), List.of(2025, 2026));
    private final UUID college = UUID.randomUUID();
    private CxoAiClient ai;
    private CxoAiCache cache;
    private DashboardPlanner planner;

    @BeforeEach
    void setUp() {
        ai = mock(CxoAiClient.class);
        cache = mock(CxoAiCache.class); // Optional-returning methods answer Optional.empty() by default
        planner = new DashboardPlanner(ai, catalog, sanitizer, json, cache, new ScopeEnforcer(catalog, sanitizer));
    }

    private void aiReplies(String reply) throws Exception {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.of(json.readTree(reply)));
    }

    @Test
    void aiSpecIsUsedAndCleaned() throws Exception {
        aiReplies("""
                {"title": "Placement health", "summary": "How placements are going",
                 "widgets": [
                   {"title": "Placement by branch", "type": "BAR", "metric": "placement_rate", "dimension": "BRANCH", "size": "lg"},
                   {"title": "Students", "type": "KPI", "metric": "students_total", "dimension": "NONE", "batchYear": 2026},
                   {"title": "Hack", "type": "BAR", "metric": "students_total; drop table users", "dimension": "BRANCH"},
                   {"title": "Names", "type": "TABLE", "metric": "offers", "dimension": "STUDENT_NAME"},
                   {"title": "Trend as donut", "type": "DONUT", "metric": "applications", "dimension": "MONTH"},
                   {"title": "Line by company", "type": "LINE", "metric": "offers", "dimension": "COMPANY", "branchCode": "x' or '1'='1"},
                   {"title": "Bad batch", "type": "KPI", "metric": "placed_students", "dimension": "NONE", "batchYear": 1999, "branchCode": "comp"}
                 ]}""");
        DashboardPlanner.Plan p = planner.plan(college, "placement health", null, ref);

        assertThat(p.source()).isEqualTo("AI");
        List<WidgetSpec> w = p.spec().widgets();
        assertThat(w).hasSize(6);                                    // the injected metric is dropped
        assertThat(w.get(0).type()).isEqualTo(WidgetType.KPI);       // KPI tiles move to the front
        assertThat(w).extracting(WidgetSpec::id).containsExactly("w1", "w2", "w3", "w4", "w5", "w6");
        WidgetSpec names = w.stream().filter(x -> x.title().equals("Names")).findFirst().orElseThrow();
        assertThat(names.dimension()).isNotEqualTo(Dimension.NONE).isIn(catalog.dimensions(catalog.find("offers").orElseThrow()));
        assertThat(w.stream().filter(x -> x.title().equals("Trend as donut")).findFirst().orElseThrow().type()).isEqualTo(WidgetType.LINE);
        WidgetSpec line = w.stream().filter(x -> x.title().equals("Line by company")).findFirst().orElseThrow();
        assertThat(line.type()).isEqualTo(WidgetType.BAR);           // LINE needs MONTH
        assertThat(line.branchCode()).isNull();                      // not a branch of this college
        WidgetSpec bad = w.stream().filter(x -> x.title().equals("Bad batch")).findFirst().orElseThrow();
        assertThat(bad.batchYear()).isNull();                        // unknown batch dropped
        assertThat(bad.branchCode()).isEqualTo("COMP");              // case-insensitive match to the real code
    }

    @Test
    void fallsBackToRulesWhenTheModelFailsOrIsOff() {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        DashboardPlanner.Plan p = planner.plan(college, "Average CTC and packages by company for batch 2026 in COMP", null, ref);
        assertThat(p.source()).isEqualTo("RULES");
        assertThat(p.spec().widgets()).isNotEmpty();
        assertThat(p.spec().widgets()).allMatch(x -> Integer.valueOf(2026).equals(x.batchYear()) && "COMP".equals(x.branchCode()));
        assertThat(p.spec().widgets()).anyMatch(x -> x.metric().equals("offers_by_ctc"));
    }

    @Test
    void modelReplyWithNothingUsableFallsBackToRules() throws Exception {
        aiReplies("{\"title\": \"x\", \"widgets\": [{\"metric\": \"salary_of_each_student\"}]}");
        assertThat(planner.plan(college, "show everything", null, ref).source()).isEqualTo("RULES");
    }

    @Test
    void followUpWithoutAiAppliesTheFilterAndKeepsWidgets() {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        DashboardSpec first = planner.plan(college, "placement overview", null, ref).spec();
        DashboardSpec second = planner.plan(college, "only batch 2025", first, ref).spec();
        assertThat(second.widgets()).hasSameSizeAs(first.widgets());
        assertThat(second.widgets()).allMatch(x -> Integer.valueOf(2025).equals(x.batchYear()));
    }

    @Test
    void queriesAreParameterizedAndScopedToTheCollege() {
        UUID college = UUID.randomUUID();
        WidgetSpec w = new WidgetSpec("w1", "t", WidgetType.BAR, "offers", Dimension.COMPANY, 2026, "COMP", null, null, 50, "md");
        MetricCatalog.Query q = catalog.build(catalog.find("offers").orElseThrow(), w, college);
        assertThat(q.sql()).doesNotContain("2026").doesNotContain("COMP'").doesNotContain(college.toString());
        assertThat(q.args()).startsWith(college.toString()).contains(2026, "COMP");
        assertThat(q.args().get(q.args().size() - 1)).isEqualTo(20);   // limit clamped
        assertThat(q.sql()).contains("o.college_id = ?");
    }

    @Test
    void everyMetricBuildsForEveryDimension() {
        UUID college = UUID.randomUUID();
        catalog.infos().forEach(m -> m.dimensions().forEach(d -> {
            MetricCatalog.Def def = catalog.find(m.key()).orElseThrow();
            WidgetSpec w = new WidgetSpec("w", "t", d == Dimension.NONE ? WidgetType.KPI : WidgetType.BAR, m.key(), d, 2026, "COMP", null, null, null, "md");
            if (catalog.isFunnel(def)) assertThat(catalog.funnel(w, college)).hasSize(5);
            else assertThat(catalog.build(def, w, college).sql()).startsWith("select ").contains("?");
        }));
    }

    @Test
    void halfWidthChartAloneOnItsRowBecomesFullWidth() {
        WidgetSpec md = new WidgetSpec(null, "a", WidgetType.BAR, "offers", Dimension.COMPANY, null, null, null, null, null, "md");
        WidgetSpec lg = new WidgetSpec(null, "b", WidgetType.BAR, "offers", Dimension.BRANCH, null, null, null, null, null, "lg");
        assertThat(SpecSanitizer.pairHalves(List.of(md, lg))).extracting(WidgetSpec::size).containsExactly("lg", "lg");
        assertThat(SpecSanitizer.pairHalves(List.of(md, md, lg, md))).extracting(WidgetSpec::size).containsExactly("md", "md", "lg", "lg");
        assertThat(SpecSanitizer.pairHalves(List.of(md, md))).extracting(WidgetSpec::size).containsExactly("md", "md");
    }

    @Test
    void cachedPlanIsUsedWithoutCallingTheModel() throws Exception {
        String cached = json.writeValueAsString(new com.proveyu.cxo.domain.CxoModel.DashboardSpec("Cached", null, List.of(
                new WidgetSpec(null, "Students", WidgetType.KPI, "students_total", Dimension.NONE, null, null, null, null, null, "sm"))));
        when(cache.get(org.mockito.ArgumentMatchers.eq(college), org.mockito.ArgumentMatchers.eq(CxoAiCache.Kind.PLAN), anyString()))
                .thenReturn(Optional.of(cached));
        DashboardPlanner.Plan p = planner.plan(college, "Placement   OVERVIEW!", null, ref);
        assertThat(p.cached()).isTrue();
        assertThat(p.spec().title()).isEqualTo("Cached");
        org.mockito.Mockito.verifyNoInteractions(ai);
    }

    @Test
    void modelAnswerIsCachedUnderTheNormalizedQuestion() throws Exception {
        aiReplies("{\"title\": \"T\", \"widgets\": [{\"type\": \"KPI\", \"metric\": \"offers\", \"dimension\": \"NONE\"}]}");
        planner.plan(college, "Placement   OVERVIEW", null, ref);
        String expected = CxoAiCache.key("plan-v7", CxoAiCache.normalize("placement overview"), "");
        org.mockito.Mockito.verify(cache).put(org.mockito.ArgumentMatchers.eq(college), org.mockito.ArgumentMatchers.eq(CxoAiCache.Kind.PLAN),
                org.mockito.ArgumentMatchers.eq(expected), anyString());
    }

    @Test
    void duplicateWidgetsAreDroppedAndTimeTitlesMustMatchTheDimension() throws Exception {
        aiReplies("""
                {"title": "Drives", "widgets": [
                  {"title": "Active drives", "type": "DONUT", "metric": "drives", "dimension": "STATUS"},
                  {"title": "Cancelled drives", "type": "DONUT", "metric": "drives", "dimension": "STATUS"},
                  {"title": "Monthly placement rate trend", "type": "BAR", "metric": "placement_rate", "dimension": "BRANCH"},
                  {"title": "Offers over time", "type": "LINE", "metric": "offers", "dimension": "MONTH"}]}""");
        List<WidgetSpec> w = planner.plan(college, "drives", null, ref).spec().widgets();
        assertThat(w).extracting(x -> x.metric() + x.dimension()).doesNotHaveDuplicates();
        assertThat(w).filteredOn(x -> x.metric().equals("drives") && x.dimension() == Dimension.STATUS).singleElement()
                .extracting(WidgetSpec::title).isEqualTo("Placement drives by status");
        assertThat(w).filteredOn(x -> x.metric().equals("placement_rate") && x.dimension() == Dimension.BRANCH).singleElement()
                .extracting(WidgetSpec::title).asString().doesNotContainIgnoringCase("month").doesNotContainIgnoringCase("trend");
        assertThat(w).anyMatch(x -> x.title().equals("Offers over time"));
    }

    @Test
    void rulesAnswerUnplacedSelectionAndPartnerQuestionsAndFlagOffTopicOnes() {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        assertThat(planner.plan(college, "kitne students abhi tak unplaced hai branch wise", null, ref).spec().widgets())
                .anyMatch(x -> x.metric().equals("unplaced_students") && x.dimension() == Dimension.BRANCH);
        assertThat(planner.plan(college, "selection rate by company", null, ref).spec().widgets())
                .anyMatch(x -> x.metric().equals("selection_rate") && x.dimension() == Dimension.COMPANY);
        assertThat(planner.plan(college, "which companies have we partnered with, MOU status", null, ref).spec().widgets())
                .anyMatch(x -> x.metric().equals("companies") && x.dimension() == Dimension.STATUS);
        DashboardSpec weather = planner.plan(college, "what is the weather in Mumbai today", null, ref).spec();
        assertThat(weather.summary()).contains("placement data");
        assertThat(weather.widgets()).extracting(x -> x.metric() + x.dimension() + x.batchYear() + x.branchCode()).doesNotHaveDuplicates();
    }

    @Test
    void donutsAreOnlyForCountsOverFewCategories() throws Exception {
        aiReplies("""
                {"title": "D", "widgets": [
                  {"title": "Rate", "type": "DONUT", "metric": "placement_rate", "dimension": "BRANCH"},
                  {"title": "Funnel", "type": "DONUT", "metric": "placement_funnel", "dimension": "STAGE"},
                  {"title": "Bands", "type": "DONUT", "metric": "offers_by_ctc", "dimension": "CTC_BAND"},
                  {"title": "Rounds", "metric": "round_pass_rate", "dimension": "ROUND"}]}""");
        List<WidgetSpec> w = planner.plan(college, "donuts", null, ref).spec().widgets();
        java.util.function.Function<String, WidgetType> typeOf = t -> w.stream().filter(x -> x.title().equals(t)).findFirst().orElseThrow().type();
        assertThat(typeOf.apply("Rate")).isEqualTo(WidgetType.BAR);
        assertThat(typeOf.apply("Funnel")).isEqualTo(WidgetType.BAR);
        assertThat(typeOf.apply("Bands")).isEqualTo(WidgetType.DONUT);
        assertThat(typeOf.apply("Rounds")).isEqualTo(WidgetType.BAR); // no type given: the default must not be a donut for a rate
    }

    @Test
    void thinModelAnswerIsToppedUpFromTheKeywordPlanner() throws Exception {
        aiReplies("""
                {"title": "Drives", "widgets": [
                  {"title": "Drives", "type": "KPI", "metric": "drives", "dimension": "NONE"},
                  {"title": "Drives by status", "type": "DONUT", "metric": "drives", "dimension": "STATUS"},
                  {"title": "Long trend", "type": "LINE", "metric": "offers", "dimension": "MONTH", "from": "2019-01-01"}]}""");
        List<WidgetSpec> w = planner.plan(college, "status of placement drives over the last years", null, ref).spec().widgets();
        assertThat(w.size()).isGreaterThanOrEqualTo(DashboardPlanner.MIN_WIDGETS);
        assertThat(w.subList(0, 4)).allMatch(x -> x.type() == WidgetType.KPI);
        assertThat(w).extracting(x -> x.metric() + x.dimension()).doesNotHaveDuplicates();
        WidgetSpec trend = w.stream().filter(x -> x.title().equals("Long trend")).findFirst().orElseThrow();
        assertThat(trend.from()).isAfterOrEqualTo(java.time.LocalDate.now().withDayOfMonth(1).minusMonths(23));
    }

    @Test
    void datesTheUserDidNotAskForAreDropped() throws Exception {
        String today = java.time.LocalDate.now().toString(), yearAgo = java.time.LocalDate.now().minusYears(1).toString();
        aiReplies("""
                {"title": "Drives", "widgets": [
                  {"title": "Drives by status", "type": "DONUT", "metric": "drives", "dimension": "STATUS", "from": "%s", "to": "%s"}]}"""
                .formatted(yearAgo, today));
        WidgetSpec plain = planner.plan(college, "status of placement drives", null, ref).spec().widgets().stream()
                .filter(x -> x.metric().equals("drives") && x.dimension() == Dimension.STATUS).findFirst().orElseThrow();
        assertThat(plain.from()).isNull();
        assertThat(plain.to()).isNull();
        WidgetSpec asked = planner.plan(college, "drives in the last year by status", null, ref).spec().widgets().stream()
                .filter(x -> x.metric().equals("drives") && x.dimension() == Dimension.STATUS).findFirst().orElseThrow();
        assertThat(asked.from()).isEqualTo(yearAgo);
        assertThat(asked.to()).isNull(); // an end of "today" stays open so upcoming drives still count
    }

    @Test
    void studentListIsAlwaysATableAndHasNoSingleNumber() throws Exception {
        assertThat(catalog.dimensions(catalog.find("unplaced_list").orElseThrow())).containsExactly(Dimension.STUDENT);
        aiReplies("""
                {"title": "L", "widgets": [{"title": "Who is left", "type": "KPI", "metric": "unplaced_list", "dimension": "NONE", "limit": 500}]}""");
        WidgetSpec list = planner.plan(college, "list unplaced students", null, ref).spec().widgets().stream()
                .filter(x -> x.metric().equals("unplaced_list")).findFirst().orElseThrow();
        assertThat(list.type()).isEqualTo(WidgetType.TABLE);
        assertThat(list.dimension()).isEqualTo(Dimension.STUDENT);
        assertThat(list.limit()).isEqualTo(50);
        MetricCatalog.Query q = catalog.build(catalog.find("unplaced_list").orElseThrow(), list, college);
        assertThat(q.sql()).doesNotContain(college.toString());
        assertThat(q.args().get(q.args().size() - 1)).isEqualTo(50);
    }

    @Test
    void rulesCoverRoundsAcceptanceAnnouncementsProctoringAndLists() {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        java.util.function.Function<String, List<String>> metrics = q -> planner.plan(college, q, null, ref).spec().widgets().stream()
                .map(x -> x.metric() + "/" + x.dimension()).toList();
        assertThat(metrics.apply("kis round mein sabse zyada students drop hue")).contains("round_pass_rate/ROUND", "round_pass_rate/NONE");
        assertThat(metrics.apply("how many offers were accepted or declined")).contains("offer_acceptance_rate/NONE", "offer_acceptance_rate/COMPANY");
        assertThat(metrics.apply("announcements kitne students ne read kiye")).contains("announcement_read_rate/ANNOUNCEMENT");
        assertThat(metrics.apply("any cheating in tests? proctoring flags")).contains("proctoring_flags/NONE", "proctoring_flags/TYPE");
        assertThat(metrics.apply("unplaced students ki list naam ke saath")).contains("unplaced_list/STUDENT");
        assertThat(metrics.apply("off campus applications by job")).contains("offcampus_applications/JOB");
    }

    @Test
    void studentNamesAreNeverSentToTheModel() {
        InsightWriter writer = new InsightWriter(ai, json, cache);
        org.mockito.ArgumentCaptor<String> sent = org.mockito.ArgumentCaptor.forClass(String.class);
        when(ai.json(anyString(), sent.capture(), anyDouble())).thenReturn(Optional.empty());
        WidgetSpec list = new WidgetSpec("w1", "Unplaced", WidgetType.TABLE, "unplaced_list", Dimension.STUDENT, null, null, null, null, 25, "lg");
        writer.write(college, "who is unplaced", new DashboardSpec("D", null, List.of(list)), java.util.Map.of("w1",
                new com.proveyu.cxo.domain.CxoModel.WidgetData("w1", com.proveyu.cxo.domain.CxoModel.Unit.CGPA, null,
                        List.of("Asha Rao (21CE01) · COMP · 2026"), List.of(8.4))));
        assertThat(sent.getValue()).doesNotContain("Asha").doesNotContain("21CE01").contains("names_shown").contains("not a count");
    }

    @Test
    void refiningAFullDashboardKeepsWhatWasJustAskedFor() throws Exception {
        List<WidgetSpec> full = new java.util.ArrayList<>();
        String[] kpis = {"students_total", "placed_students", "placement_rate", "avg_ctc"};
        for (String k : kpis) full.add(new WidgetSpec(null, k, WidgetType.KPI, k, Dimension.NONE, null, null, null, null, null, "sm"));
        String[][] charts = {{"offers", "MONTH"}, {"offers", "COMPANY"}, {"drives", "STATUS"}, {"avg_drive_ctc", "COMPANY"}, {"placement_funnel", "STAGE"},
                {"placement_rate", "BRANCH"}, {"offers_by_ctc", "CTC_BAND"}, {"avg_ctc", "BRANCH"}};
        for (String[] c : charts) full.add(new WidgetSpec(null, c[0], WidgetType.BAR, c[0], Dimension.valueOf(c[1]), null, null, null, null, null, "md"));
        DashboardSpec current = sanitizer.clean(new DashboardSpec("Full", null, full), ref);
        assertThat(current.widgets()).hasSize(12);
        // the model keeps all 12 and appends the list at the end (13 widgets)
        StringBuilder w = new StringBuilder("{\"title\": \"Full\", \"widgets\": [");
        for (WidgetSpec x : current.widgets()) w.append(json.writeValueAsString(x)).append(',');
        w.append("{\"title\": \"Unplaced\", \"type\": \"TABLE\", \"metric\": \"unplaced_list\", \"dimension\": \"STUDENT\"}]}");
        aiReplies(w.toString());
        List<WidgetSpec> out = planner.plan(college, "unplaced students ki list do", current, ref).spec().widgets();
        assertThat(out).hasSize(12).anyMatch(x -> x.metric().equals("unplaced_list"));
    }

    @Test
    void titlesThatContradictTheWidgetAreReplaced() {
        Unit pct = Unit.PERCENT, cnt = Unit.COUNT, lpa = Unit.LPA;
        // wrong
        assertThat(TitleCheck.fits("Flagged Attempts by Month vs Score", "mock_test_avg_score", pct, Dimension.MONTH)).isFalse();
        assertThat(TitleCheck.fits("Average CTC by branch", "highest_ctc", lpa, Dimension.BRANCH)).isFalse();
        assertThat(TitleCheck.fits("Placement rate by branch", "placed_students", cnt, Dimension.BRANCH)).isFalse();
        assertThat(TitleCheck.fits("Offers by branch", "offers", cnt, Dimension.MONTH)).isFalse();
        assertThat(TitleCheck.fits("Monthly placement rate trend", "placement_rate", pct, Dimension.BRANCH)).isFalse();
        assertThat(TitleCheck.fits("Top recruiters", "offers", cnt, Dimension.MONTH)).isFalse();
        assertThat(TitleCheck.fits("Offers vs applications", "offers", cnt, Dimension.COMPANY)).isFalse();
        // right
        for (String[] ok : new String[][] {{"Top recruiters by offers", "offers", "COUNT", "COMPANY"}, {"Offers by package", "offers_by_ctc", "COUNT", "CTC_BAND"},
                {"Highest CTC by company", "highest_ctc", "LPA", "COMPANY"}, {"Placement rate %", "placement_rate", "PERCENT", "NONE"},
                {"Pass rate by round", "round_pass_rate", "PERCENT", "ROUND"}, {"Read rate by announcement", "announcement_read_rate", "PERCENT", "ANNOUNCEMENT"},
                {"Flags by type", "proctoring_flags", "COUNT", "TYPE"}, {"Unplaced students (COMP)", "unplaced_list", "CGPA", "STUDENT"},
                {"Total Enrolled Students", "students_total", "COUNT", "NONE"}, {"Offers Trend Over Months", "offers", "COUNT", "MONTH"},
                {"Recruiters by MoU status", "companies", "COUNT", "STATUS"}, {"Average score by test", "mock_test_avg_score", "PERCENT", "TEST"},
                {"Placement funnel", "placement_funnel", "COUNT", "STAGE"}, {"Applications by job", "offcampus_applications", "COUNT", "JOB"}}) {
            assertThat(TitleCheck.fits(ok[0], ok[1], Unit.valueOf(ok[2]), Dimension.valueOf(ok[3]))).as(ok[0]).isTrue();
        }
    }

    @Test
    void batchesAreListedInYearOrder() {
        WidgetSpec w = new WidgetSpec(null, "Placement rate by batch", WidgetType.BAR, "placement_rate", Dimension.BATCH, null, null, null, null, null, "md");
        assertThat(catalog.build(catalog.find("placement_rate").orElseThrow(), w, college).sql()).contains("order by 1 asc");
    }

    @Test
    void theKeywordPlannersOwnTitlesAllPassTheCheck() {
        String[] prompts = {"placement overview", "packages ctc salary by branch and company", "monthly trend of offers", "drives status",
                "funnel and selection rate", "mock test performance", "ai interview practice", "interviews", "off campus by job",
                "unplaced students list names", "rounds drop off", "offer acceptance declined", "announcements read", "proctoring cheating flags",
                "mou partners companies"};
        for (String p : prompts) {
            for (WidgetSpec w : planner.rules(p, ref).widgets()) {
                MetricCatalog.Def d = catalog.find(w.metric()).orElseThrow();
                assertThat(TitleCheck.fits(w.title(), w.metric(), d.unit(), w.dimension())).as(p + " -> " + w.title()).isTrue();
            }
        }
    }

    @Test
    void aComparisonOfNamedYearsShowsExactlyThoseYearsSideBySide() throws Exception {
        aiReplies("""
                {"title": "Compare", "widgets": [
                  {"title": "2026 Placement Rate", "type": "KPI", "metric": "placement_rate", "dimension": "BATCH", "batchYear": 2026},
                  {"title": "Offers", "type": "KPI", "metric": "offers", "dimension": "NONE"},
                  {"title": "Drives", "type": "KPI", "metric": "drives", "dimension": "NONE"},
                  {"title": "Funnel", "type": "BAR", "metric": "placement_funnel", "dimension": "STAGE"},
                  {"title": "Placement rate by batch", "type": "BAR", "metric": "placement_rate", "dimension": "BATCH", "batchYear": 2026}]}""");
        List<WidgetSpec> w = planner.plan(college, "2024 vs 2026 placement compare karo", null, ref).spec().widgets();
        assertThat(w).isNotEmpty().allMatch(x -> x.batches() != null && x.batches().equals(List.of(2024, 2026)));
        assertThat(w).allMatch(x -> x.type() != WidgetType.KPI && x.batchYear() == null);
        assertThat(w).extracting(WidgetSpec::metric).contains("placement_rate", "offers", "drives");
        assertThat(w).filteredOn(x -> x.metric().equals("drives")).allMatch(x -> x.dimension() == Dimension.YEAR); // only has a date: calendar years
        assertThat(w).filteredOn(x -> x.metric().equals("offers")).allMatch(x -> x.dimension() == Dimension.BATCH);
    }

    @Test
    void oneYearAsksOnlyForThatYearEverywhere() throws Exception {
        aiReplies("""
                {"title": "T", "widgets": [
                  {"title": "Offers", "type": "KPI", "metric": "offers", "dimension": "NONE", "batchYear": 2026},
                  {"title": "Drives", "type": "KPI", "metric": "drives", "dimension": "NONE"},
                  {"title": "Recruiters", "type": "KPI", "metric": "companies", "dimension": "NONE"},
                  {"title": "By batch", "type": "BAR", "metric": "offers", "dimension": "BATCH"},
                  {"title": "Offers by company", "type": "BAR", "metric": "offers", "dimension": "COMPANY", "from": "2020-01-01"}]}""");
        List<WidgetSpec> w = planner.plan(college, "placement 2024", null, ref).spec().widgets(); // 2024 is not a batch of this college
        assertThat(w).extracting(WidgetSpec::metric).doesNotContain("companies"); // cannot be limited to a year
        assertThat(w).noneMatch(x -> x.dimension() == Dimension.BATCH);
        for (WidgetSpec x : w) {
            if (x.metric().equals("drives")) {
                assertThat(x.batchYear()).isNull();
                assertThat(x.from()).isEqualTo(java.time.LocalDate.of(2024, 1, 1));
                assertThat(x.to()).isEqualTo(java.time.LocalDate.of(2024, 12, 31));
            } else {
                assertThat(x.batchYear()).isEqualTo(2024); // kept although not on record: it then has no numbers
                assertThat(x.from()).isNull();
            }
        }
    }

    @Test
    void allYearsMeansEveryBatchOnRecord() {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        List<WidgetSpec> w = planner.plan(college, "compare placements across all years", null, ref).spec().widgets();
        assertThat(w).isNotEmpty().allMatch(x -> x.batches() != null && x.batches().equals(List.of(2025, 2026)));
        assertThat(planner.plan(college, "placement since 2025", null, ref).spec().widgets()).allMatch(x -> x.batches() != null && x.batches().contains(2025));
        assertThat(planner.plan(college, "placement from 2024 to 2026", null, ref).spec().widgets())
                .allMatch(x -> x.batches() != null && x.batches().equals(List.of(2024, 2025, 2026)));
    }

    @Test
    void aQuestionWithoutAYearIsLeftAlone() {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        assertThat(planner.plan(college, "placement overview", null, ref).spec().widgets()).allMatch(x -> x.batchYear() == null && x.batches() == null);
    }

    @Test
    void followUpsKeepTheYearScopeOfWhatIsAsked() {
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        DashboardSpec open = planner.plan(college, "placement overview batch 2025", null, ref).spec();
        assertThat(open.widgets()).filteredOn(x -> x.metric().equals("students_total")).allMatch(x -> Integer.valueOf(2025).equals(x.batchYear()));
        DashboardSpec cmp = planner.plan(college, "compare 2025 vs 2026", open, ref).spec();
        assertThat(cmp.widgets()).allMatch(x -> x.batches() != null && x.batches().equals(List.of(2025, 2026)));
        assertThat(cmp.title()).isEqualTo("Placement overview · batches 2025, 2026");
        DashboardSpec again = planner.plan(college, "only batch 2025", cmp, ref).spec();
        assertThat(again.title()).isEqualTo("Placement overview · batch 2025");
        assertThat(again.widgets()).allMatch(x -> x.batches() == null);
    }

    @Test
    void liveNumbersKeepTheClientsWidgetIdsEvenWhenFiltersNoLongerApply() {
        // a hack metric and a bad branch are dropped / cleared, and each widget keeps its own id
        DashboardSpec onScreen = new DashboardSpec("D", null, List.of(
                new WidgetSpec("w1", "Students 2030", WidgetType.KPI, "students_total", Dimension.NONE, 2030, null, null, null, null, "sm"),
                new WidgetSpec("w2", "Students", WidgetType.KPI, "students_total", Dimension.NONE, null, null, null, null, null, "sm"),
                new WidgetSpec("w3", "Hack", WidgetType.BAR, "students_total; drop table users", Dimension.BRANCH, null, null, null, null, null, "md"),
                new WidgetSpec("w4", "Offers", WidgetType.BAR, "offers", Dimension.COMPANY, null, "x' or '1'='1", null, null, null, "md")));
        List<WidgetSpec> run = sanitizer.forRun(onScreen, ref);
        assertThat(run).extracting(WidgetSpec::id).containsExactly("w1", "w2", "w4");
        assertThat(run.get(0).batchYear()).isEqualTo(2030); // a year that is not on record is kept: it has no numbers, not all numbers
        assertThat(run.get(2).branchCode()).isNull();
    }
}
