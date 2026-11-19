package com.proveyu.offcampus.application;

import com.proveyu.offcampus.domain.OffCampusModel.AppStatus;
import com.proveyu.offcampus.domain.OffCampusModel.Policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Pure rules for off-campus jobs: who sees a job, who may apply, and which status changes a student may make. No database. */
public final class OffCampusRules {
    private OffCampusRules() {}

    /** A job's eligibility filters. Empty lists and nulls mean "no restriction". */
    public record Criteria(List<Integer> batchYears, List<String> degreeKeywords, List<String> branchKeywords, BigDecimal minCgpa,
                           Integer maxBacklogs, Set<UUID> collegeIds) {}

    /** What the rules need to know about a student. */
    public record Student(UUID collegeId, Integer batchYear, String degree, String branch, BigDecimal cgpa, int activeBacklogs, String status,
                          Policy policy) {}

    public record Result(boolean eligible, List<String> reasons, boolean canApply) {}

    /** True when the job is offered to the student's college at all (otherwise the student never sees it). */
    public static boolean visibleTo(Criteria c, Student s) {
        return s.policy() != Policy.DISABLED && (c.collegeIds() == null || c.collegeIds().isEmpty() || c.collegeIds().contains(s.collegeId()));
    }

    public static Result check(Criteria c, Student s) {
        List<String> reasons = new ArrayList<>();
        if (c.batchYears() != null && !c.batchYears().isEmpty() && (s.batchYear() == null || !c.batchYears().contains(s.batchYear()))) {
            reasons.add("Only for the " + join(c.batchYears()) + " batch" + (c.batchYears().size() > 1 ? "es" : ""));
        }
        if (!matches(c.degreeKeywords(), s.degree())) reasons.add("Only for " + String.join(" / ", c.degreeKeywords()) + " students");
        if (!matches(c.branchKeywords(), s.branch())) reasons.add("Only for " + String.join(" / ", c.branchKeywords()) + " branches");
        if (c.minCgpa() != null && c.minCgpa().signum() > 0) {
            if (s.cgpa() == null) reasons.add("Needs a CGPA of " + c.minCgpa().stripTrailingZeros().toPlainString() + "+ (yours is not on record)");
            else if (s.cgpa().compareTo(c.minCgpa()) < 0) reasons.add("Needs a CGPA of " + c.minCgpa().stripTrailingZeros().toPlainString() + "+");
        }
        if (c.maxBacklogs() != null && s.activeBacklogs() > c.maxBacklogs()) {
            reasons.add(c.maxBacklogs() == 0 ? "No active backlogs allowed" : "At most " + c.maxBacklogs() + " active backlog" + (c.maxBacklogs() == 1 ? "" : "s"));
        }
        boolean eligible = reasons.isEmpty();
        if (s.policy() == Policy.BLOCK_PLACED && "PLACED".equals(s.status())) {
            reasons.add("Your placement cell does not allow placed students to apply off-campus");
        }
        if ("DEBARRED".equals(s.status())) reasons.add("Your placement cell has debarred you from placements");
        return new Result(eligible, List.copyOf(reasons), reasons.isEmpty());
    }

    /**
     * A student may record any status except CLICKED (ProveYu sets that). Before the first status, only APPLIED or NOT_INTERESTED make
     * sense; after that the student may correct it freely (for example from REJECTED back to INTERVIEWING).
     */
    public static boolean canMove(AppStatus from, AppStatus to) {
        if (to == null || to == AppStatus.CLICKED) return false;
        if (from == null || from == AppStatus.CLICKED || from == AppStatus.NOT_INTERESTED) {
            return to == AppStatus.APPLIED || to == AppStatus.NOT_INTERESTED;
        }
        return true;
    }

    /** "Computer, IT" -> [computer, it]; blank -> []. */
    public static List<String> keywords(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(k -> !k.isEmpty()).distinct().toList();
    }

    /**
     * True when no keyword is set, or any keyword appears in the value. Short keywords (up to 3 letters, like "IT" or "CS") must match a
     * whole word or the value's initials ("Information Technology" -> "it"), so "IT" does not match "Electrical".
     */
    static boolean matches(List<String> keywords, String value) {
        if (keywords == null || keywords.isEmpty()) return true;
        if (value == null || value.isBlank()) return false;
        String v = value.toLowerCase(Locale.ROOT);
        List<String> words = Arrays.stream(v.split("[^a-z0-9.]+")).filter(w -> !w.isEmpty()).toList();
        StringBuilder initials = new StringBuilder();
        for (String w : words) if (!Set.of("and", "of", "in", "&").contains(w)) initials.append(w.charAt(0));
        for (String k : keywords) {
            String key = k.toLowerCase(Locale.ROOT).trim();
            if (key.isEmpty()) continue;
            if (key.replace(".", "").length() <= 3) {
                String bare = key.replace(".", "");
                if (words.stream().anyMatch(w -> w.replace(".", "").equals(bare)) || initials.toString().equals(bare)) return true;
            } else if (v.contains(key) || v.replaceAll("[.\\s-]", "").contains(key.replaceAll("[.\\s-]", ""))) { // "B.Tech" = "BTech" = "B Tech"
                return true;
            }
        }
        return false;
    }

    private static String join(List<Integer> years) {
        return String.join(" / ", years.stream().sorted().map(String::valueOf).toList());
    }
}
