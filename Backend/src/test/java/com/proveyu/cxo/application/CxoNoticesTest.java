package com.proveyu.cxo.application;

import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The notes shown above a dashboard about the data behind it. */
class CxoNoticesTest {

    private final CollegeRef ref = new CollegeRef(List.of("COMP"), List.of(2026, 2027));
    private final WidgetSpec kpi = new WidgetSpec("w1", "Placed", WidgetType.KPI, "placed_students", Dimension.NONE, null, null, null, null, null, "sm");
    private final Map<String, WidgetData> some = Map.of("w1", new WidgetData("w1", Unit.COUNT, 11.0, List.of(), List.of()));

    @Test
    void aBatchThatIsNotOnRecordIsNamedAndNothingIsShownForIt() {
        List<String> n = CxoDashboardService.notices("2025 batch ka placement", new DashboardSpec("D", null, List.of(kpi)), some, ref, "LTCE");
        assertThat(n).hasSize(2);
        assertThat(n.get(0)).contains("graduating that year");
        assertThat(n.get(1)).contains("no batch 2025").contains("batches 2026 and 2027").contains("Nothing is shown for it");
    }

    @Test
    void aSideBySideChartSaysTheMissingYearIsLeftOut() {
        WidgetSpec side = new WidgetSpec("w1", "Placed by batch", WidgetType.COLUMN, "placed_students", Dimension.BATCH, null, null, null, null, null, "md",
                List.of(2025, 2026));
        List<String> n = CxoDashboardService.notices("compare 2025 and 2026", new DashboardSpec("D", null, List.of(side)), some, ref, "LTCE");
        assertThat(n.get(1)).contains("no batch 2025").contains("left out of the charts");
    }

    @Test
    void yearsOnRecordNeedOnlyTheMeaningNote() {
        assertThat(CxoDashboardService.notices("2027 vs 2026", new DashboardSpec("D", null, List.of(kpi)), some, ref, "LTCE")).hasSize(1);
        assertThat(CxoDashboardService.notices("placement overview", new DashboardSpec("D", null, List.of(kpi)), some, ref, "LTCE")).isEmpty();
    }

    @Test
    void titlesKeepTheYearsAskedForAndDropOthers() {
        assertThat(CxoDashboardService.withoutMissingYears(new DashboardSpec("2025 Batch Placement Overview", null, List.of()), ref, "placement overview").title())
                .isEqualTo("Batch Placement Overview");
        assertThat(CxoDashboardService.withoutMissingYears(new DashboardSpec("Placement overview · batch 2025", null, List.of()), ref, "2025 placement").title())
                .isEqualTo("Placement overview · batch 2025");
        assertThat(CxoDashboardService.withoutMissingYears(new DashboardSpec("Batch Placement Comparison 2026-2027", null, List.of()), ref, "x").title())
                .isEqualTo("Batch Placement Comparison 2026-2027");
    }
}
