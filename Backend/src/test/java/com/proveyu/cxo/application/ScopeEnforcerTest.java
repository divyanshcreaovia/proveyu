package com.proveyu.cxo.application;

import com.proveyu.cxo.application.ScopeEnforcer.Scope;
import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How a CXO's question is read for years, and that every kind of question ends up with exactly the years asked. */
class ScopeEnforcerTest {

    private final CollegeRef ref = new CollegeRef(List.of("COMP", "MECH"), List.of(2025, 2026, 2027));

    @Test
    void readsYearsFromEnglishAndHinglish() {
        assertThat(ScopeEnforcer.parse("2025 ka placement dikhao").years()).containsExactly(2025);
        assertThat(ScopeEnforcer.parse("placement in 2026").years()).containsExactly(2026);
        assertThat(ScopeEnforcer.parse("2024 vs 2026").years()).containsExactly(2024, 2026);
        assertThat(ScopeEnforcer.parse("2026 aur 2025 ka comparison").years()).containsExactly(2025, 2026);
        assertThat(ScopeEnforcer.parse("batch 2025-26 placements").years()).containsExactly(2025); // "26" is not a year
        assertThat(ScopeEnforcer.parse("package of 20000000 or 2030000").years()).isEmpty(); // not inside a bigger number
        assertThat(ScopeEnforcer.parse("placement overview").any()).isFalse();
    }

    @Test
    void readsRangesAndSince() {
        assertThat(ScopeEnforcer.parse("from 2023 to 2025").years()).containsExactly(2023, 2024, 2025);
        assertThat(ScopeEnforcer.parse("between 2023 and 2025").years()).contains(2023, 2025);
        assertThat(ScopeEnforcer.parse("2023-2025 trend").years()).containsExactly(2023, 2024, 2025);
        List<Integer> since = ScopeEnforcer.parse("since 2024").years();
        assertThat(since.get(0)).isEqualTo(2024);
        assertThat(since).contains(LocalDate.now().getYear());
    }

    @Test
    void readsAllYearsAndPreviousYear() {
        for (String q : List.of("compare all years", "every year placement", "sab saal ka comparison", "year on year growth", "across all batches", "batch wise placement", "yoy"))
            assertThat(ScopeEnforcer.parse(q).all()).as(q).isTrue();
        assertThat(ScopeEnforcer.parse("2026 vs last year").previous()).isTrue();
        assertThat(ScopeEnforcer.parse("pichle saal se compare 2026").previous()).isTrue();
        assertThat(ScopeEnforcer.parse("placement overview").all()).isFalse();
    }

    @Test
    void whichYearsAreSideBySide() {
        assertThat(ScopeEnforcer.compared(ScopeEnforcer.parse("2025"), ref)).isEmpty();
        assertThat(ScopeEnforcer.compared(ScopeEnforcer.parse("2024 vs 2026"), ref)).containsExactly(2024, 2026);
        assertThat(ScopeEnforcer.compared(ScopeEnforcer.parse("2026 vs last year"), ref)).containsExactly(2025, 2026);
        assertThat(ScopeEnforcer.compared(ScopeEnforcer.parse("all years"), ref)).containsExactly(2025, 2026, 2027);
        assertThat(ScopeEnforcer.compared(ScopeEnforcer.parse("all years including 2024"), ref)).containsExactly(2024, 2025, 2026, 2027);
    }

    @Test
    void missingYears() {
        assertThat(ScopeEnforcer.missing(ScopeEnforcer.parse("2024 vs 2026 vs 2030"), ref)).containsExactly(2024, 2030);
    }

    @Test
    void everyKindOfQuestionKeepsToTheYearsAsked() {
        MetricCatalog catalog = new MetricCatalog();
        SpecSanitizer sanitizer = new SpecSanitizer(catalog);
        CxoAiClient ai = mock(CxoAiClient.class);
        when(ai.json(anyString(), anyString(), anyDouble())).thenReturn(Optional.empty());
        DashboardPlanner planner = new DashboardPlanner(ai, catalog, sanitizer, new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()), mock(CxoAiCache.class), new ScopeEnforcer(catalog, sanitizer));
        UUID college = UUID.randomUUID();
        List<String> topics = List.of("placement overview", "salary and ctc", "branch wise placement", "recruiters and companies", "monthly trend of offers",
                "drives", "selection funnel", "mock test performance", "ai interview practice", "interviews", "off-campus", "unplaced students list",
                "offer acceptance", "announcements read rate", "proctoring flags", "code ground practice", "cgpa", "gender wise placement", "rounds pass rate");
        for (String topic : topics) {
            // one year, also a year the college has no batch for
            for (int y : new int[]{2025, 2024}) {
                for (WidgetSpec w : planner.plan(college, topic + " " + y, null, ref).spec().widgets()) {
                    String at = topic + " " + y + " -> " + w.title();
                    assertThat(w.batches()).as(at).isNull();
                    boolean byDate = w.from() != null;
                    assertThat(byDate ? w.from() : LocalDate.of(y, 1, 1)).as(at).isEqualTo(LocalDate.of(y, 1, 1));
                    if (byDate) assertThat(w.to()).as(at).isEqualTo(LocalDate.of(y, 12, 31));
                    else assertThat(w.batchYear()).as(at).isEqualTo(y);
                    assertThat(w.dimension()).as(at).isNotIn(Dimension.BATCH, Dimension.YEAR);
                }
            }
            // named years, all years
            for (String q : List.of(topic + " compare 2024 and 2026", topic + " all years", "compare " + topic + " 2025 vs last year")) {
                List<WidgetSpec> widgets = planner.plan(college, q, null, ref).spec().widgets();
                assertThat(widgets).as(q).isNotEmpty();
                for (WidgetSpec w : widgets) {
                    assertThat(w.batches()).as(q + " -> " + w.title()).isNotEmpty();
                    assertThat(w.dimension()).as(q).isIn(Dimension.BATCH, Dimension.YEAR);
                    assertThat(w.batchYear()).as(q).isNull();
                }
            }
        }
    }
}
