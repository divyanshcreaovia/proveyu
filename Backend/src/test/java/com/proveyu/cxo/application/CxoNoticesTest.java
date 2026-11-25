package com.proveyu.cxo.application;

import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The notes shown above a dashboard about the data behind it. */
class CxoNoticesTest {

    private final CollegeRef ref = new CollegeRef(List.of("COMP"), List.of(2026, 2027));
    private final WidgetSpec kpi = new WidgetSpec("w1", "Placed", WidgetType.KPI, "placed_students", Dimension.NONE, null, null, null, null, null, "sm");
    private final Map<String, WidgetData> some = Map.of("w1", new WidgetData("w1", Unit.COUNT, 11.0, List.of(), List.of()));

    @Test
    void aBatchThatIsNotOnRecordIsNamed() {
        List<String> n = CxoDashboardService.notices("2025 batch ka placement", new DashboardSpec("D", null, List.of(kpi)), some, ref, "LTCE");
        assertThat(n).singleElement().asString()
                .contains("no batch 2025").contains("batches 2026 and 2027").contains("shows the batches on record instead");
    }

    @Test
    void aYearShownAsDatesSaysSo() {
        WidgetSpec dated = new WidgetSpec("w1", "Offers", WidgetType.KPI, "offers", Dimension.NONE, null, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null, "sm");
        List<String> n = CxoDashboardService.notices("2025 ke offers", new DashboardSpec("D", null, List.of(dated)),
                Map.of("w1", new WidgetData("w1", Unit.COUNT, 0.0, List.of(), List.of())), ref, "LTCE");
        assertThat(n).hasSize(2);
        assertThat(n.get(0)).contains("Numbers dated 2025");
        assertThat(n.get(1)).contains("Nothing has been recorded");
    }

    @Test
    void yearsOnRecordNeedNoNote() {
        assertThat(CxoDashboardService.notices("2027 vs 2026", new DashboardSpec("D", null, List.of(kpi)), some, ref, "LTCE")).isEmpty();
    }

    @Test
    void titlesDropYearsThatAreNotOnRecord() {
        assertThat(CxoDashboardService.withoutMissingYears(new DashboardSpec("2025 Batch Placement Overview", null, List.of()), ref).title())
                .isEqualTo("Batch Placement Overview");
        assertThat(CxoDashboardService.withoutMissingYears(new DashboardSpec("Batch Placement Comparison 2026-2027", null, List.of()), ref).title())
                .isEqualTo("Batch Placement Comparison 2026-2027");
    }
}
