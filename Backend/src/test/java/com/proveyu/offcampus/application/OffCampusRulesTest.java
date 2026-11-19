package com.proveyu.offcampus.application;

import com.proveyu.offcampus.application.OffCampusRules.Criteria;
import com.proveyu.offcampus.application.OffCampusRules.Result;
import com.proveyu.offcampus.application.OffCampusRules.Student;
import com.proveyu.offcampus.domain.OffCampusModel.AppStatus;
import com.proveyu.offcampus.domain.OffCampusModel.Policy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OffCampusRulesTest {

    private static final UUID COLLEGE = UUID.randomUUID();

    private static Criteria open() {
        return new Criteria(List.of(), List.of(), List.of(), null, null, Set.of());
    }

    private static Student student(String branch, String degree, Integer batch, String cgpa, int backlogs, String status, Policy policy) {
        return new Student(COLLEGE, batch, degree, branch, cgpa == null ? null : new BigDecimal(cgpa), backlogs, status, policy);
    }

    private static Student cse() {
        return student("Computer Science and Engineering", "B.Tech", 2026, "8.1", 0, "ACTIVE", Policy.ALLOW);
    }

    @Test
    void noFiltersMeansEveryoneCanApply() {
        Result r = OffCampusRules.check(open(), cse());
        assertThat(r.eligible()).isTrue();
        assertThat(r.canApply()).isTrue();
        assertThat(r.reasons()).isEmpty();
    }

    @Test
    void eachFilterGivesItsOwnReason() {
        Criteria c = new Criteria(List.of(2025), List.of("MCA"), List.of("Mechanical"), new BigDecimal("8.5"), 0, Set.of());
        Result r = OffCampusRules.check(c, student("Computer Science", "B.Tech", 2026, "7.0", 2, "ACTIVE", Policy.ALLOW));
        assertThat(r.eligible()).isFalse();
        assertThat(r.reasons()).containsExactly("Only for the 2025 batch", "Only for MCA students", "Only for Mechanical branches",
                "Needs a CGPA of 8.5+", "No active backlogs allowed");
    }

    @Test
    void missingCgpaIsExplained() {
        Criteria c = new Criteria(List.of(), List.of(), List.of(), new BigDecimal("7"), null, Set.of());
        assertThat(OffCampusRules.check(c, student("CSE", "B.Tech", 2026, null, 0, "ACTIVE", Policy.ALLOW)).reasons())
                .containsExactly("Needs a CGPA of 7+ (yours is not on record)");
    }

    @Test
    void keywordsMatchNamesInitialsAndSpellings() {
        assertThat(OffCampusRules.matches(List.of("computer"), "Computer Science and Engineering")).isTrue();
        assertThat(OffCampusRules.matches(List.of("IT"), "Information Technology")).isTrue();
        assertThat(OffCampusRules.matches(List.of("IT"), "IT")).isTrue();
        assertThat(OffCampusRules.matches(List.of("IT"), "Electrical Engineering")).isFalse(); // "it" is not a whole word there
        assertThat(OffCampusRules.matches(List.of("CSE"), "Computer Science and Engineering")).isTrue();
        assertThat(OffCampusRules.matches(List.of("B.Tech"), "BTech")).isTrue();
        assertThat(OffCampusRules.matches(List.of("btech"), "B. Tech")).isTrue();
        assertThat(OffCampusRules.matches(List.of("Mechanical", "Civil"), "Civil Engineering")).isTrue();
        assertThat(OffCampusRules.matches(List.of("Computer"), null)).isFalse();
        assertThat(OffCampusRules.matches(List.of(), null)).isTrue();
    }

    @Test
    void collegeListAndDisabledPolicyHideTheJob() {
        Criteria other = new Criteria(List.of(), List.of(), List.of(), null, null, Set.of(UUID.randomUUID()));
        Criteria mine = new Criteria(List.of(), List.of(), List.of(), null, null, Set.of(COLLEGE));
        assertThat(OffCampusRules.visibleTo(open(), cse())).isTrue();
        assertThat(OffCampusRules.visibleTo(other, cse())).isFalse();
        assertThat(OffCampusRules.visibleTo(mine, cse())).isTrue();
        assertThat(OffCampusRules.visibleTo(open(), student("CSE", "B.Tech", 2026, "8", 0, "ACTIVE", Policy.DISABLED))).isFalse();
    }

    @Test
    void placedStudentsAreBlockedOnlyWhenTheCollegeSaysSo() {
        Result allowed = OffCampusRules.check(open(), student("CSE", "B.Tech", 2026, "8", 0, "PLACED", Policy.ALLOW));
        assertThat(allowed.canApply()).isTrue();
        Result blocked = OffCampusRules.check(open(), student("CSE", "B.Tech", 2026, "8", 0, "PLACED", Policy.BLOCK_PLACED));
        assertThat(blocked.eligible()).isTrue();
        assertThat(blocked.canApply()).isFalse();
        assertThat(blocked.reasons()).containsExactly("Your placement cell does not allow placed students to apply off-campus");
        assertThat(OffCampusRules.check(open(), student("CSE", "B.Tech", 2026, "8", 0, "ACTIVE", Policy.BLOCK_PLACED)).canApply()).isTrue();
        assertThat(OffCampusRules.check(open(), student("CSE", "B.Tech", 2026, "8", 0, "DEBARRED", Policy.ALLOW)).canApply()).isFalse();
    }

    @Test
    void statusMovesStartWithAppliedAndNeverGoBackToClicked() {
        assertThat(OffCampusRules.canMove(null, AppStatus.APPLIED)).isTrue();
        assertThat(OffCampusRules.canMove(AppStatus.CLICKED, AppStatus.APPLIED)).isTrue();
        assertThat(OffCampusRules.canMove(AppStatus.CLICKED, AppStatus.NOT_INTERESTED)).isTrue();
        assertThat(OffCampusRules.canMove(AppStatus.CLICKED, AppStatus.OFFER)).isFalse();
        assertThat(OffCampusRules.canMove(null, AppStatus.INTERVIEWING)).isFalse();
        assertThat(OffCampusRules.canMove(AppStatus.APPLIED, AppStatus.INTERVIEWING)).isTrue();
        assertThat(OffCampusRules.canMove(AppStatus.INTERVIEWING, AppStatus.OFFER)).isTrue();
        assertThat(OffCampusRules.canMove(AppStatus.REJECTED, AppStatus.INTERVIEWING)).isTrue(); // the student corrects a mistake
        assertThat(OffCampusRules.canMove(AppStatus.APPLIED, AppStatus.CLICKED)).isFalse();
        assertThat(OffCampusRules.canMove(AppStatus.NOT_INTERESTED, AppStatus.APPLIED)).isTrue();
    }

    @Test
    void keywordListsAreTrimmedAndDeduplicated() {
        assertThat(OffCampusRules.keywords(" Computer , IT,, Computer ")).containsExactly("Computer", "IT");
        assertThat(OffCampusRules.keywords("  ")).isEmpty();
        assertThat(OffCampusRules.keywords(null)).isEmpty();
    }
}
