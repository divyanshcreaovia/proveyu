package com.proveyu.cxo.application;

import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Unit;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.proveyu.cxo.domain.CxoModel.Dimension.*;

/**
 * Whether a widget title (usually written by the model) says what the widget really shows. A title fails when it:
 * <ul>
 *   <li>names something only other metrics measure ("Flagged attempts" on an average score, "Average CTC" on the highest CTC),</li>
 *   <li>compares two things ("X vs Y"): a widget shows one metric,</li>
 *   <li>calls a count a rate or percentage,</li>
 *   <li>names a split the widget does not have ("... by branch" on a monthly chart, "Monthly ..." on a ranking).</li>
 * </ul>
 * A failing title is replaced with one built from the metric and its split, so the label never contradicts the numbers.
 */
final class TitleCheck {

    private TitleCheck() {}

    private static Pattern words(String regex) {
        return Pattern.compile("\\b(" + regex + ")", Pattern.CASE_INSENSITIVE);
    }

    /** Words that only some metrics may use, with those metrics. */
    private static final Map<Pattern, Set<String>> OWNED = Map.ofEntries(
            Map.entry(words("flag|proctor|cheat|integrity|malpractice|violation"), Set.of("proctoring_flags", "flagged_attempts")),
            Map.entry(words("ctc|package|salary|lpa|compensation"), Set.of("avg_ctc", "highest_ctc", "offers_by_ctc", "avg_drive_ctc")),
            Map.entry(words("score|marks"), Set.of("mock_test_avg_score", "ai_interview_score")),
            Map.entry(words("unplaced|not placed|yet to be placed"), Set.of("unplaced_students", "unplaced_list")),
            Map.entry(words("funnel"), Set.of("placement_funnel")),
            Map.entry(words("round"), Set.of("round_results", "round_pass_rate")),
            Map.entry(words("announcement|notice|circular"), Set.of("announcements", "announcement_read_rate")),
            Map.entry(words("off[- ]?campus"), Set.of("offcampus_applications")),
            Map.entry(words("accept|declin"), Set.of("offer_acceptance_rate", "offers")),
            Map.entry(words("mou|partner"), Set.of("companies")),
            Map.entry(words("ai interview|ai mock|mock interview"), Set.of("ai_interviews", "ai_interview_score")),
            Map.entry(words("highest|maximum|max\\b|peak"), Set.of("highest_ctc", "unplaced_list")),
            Map.entry(words("average|avg\\b|mean"), Set.of("avg_ctc", "avg_drive_ctc", "mock_test_avg_score", "ai_interview_score")),
            Map.entry(words("selection rate|conversion"), Set.of("selection_rate", "placement_funnel")),
            Map.entry(words("read rate|opened|read by"), Set.of("announcement_read_rate")));

    private static final Pattern COMPARISON = words("vs\\.?|versus|compared (to|with)");
    private static final Pattern RATE = words("rate|percent|percentage|ratio|share")
            ;
    private static final Pattern PERCENT_SIGN = Pattern.compile("%");

    /** Phrases that announce a split ("by branch", "branch-wise", "per month", "top recruiters"). */
    private static final Map<Dimension, Pattern> SPLIT = Map.ofEntries(
            Map.entry(BRANCH, split("branch(es)?|department(s)?|stream(s)?")),
            Map.entry(COMPANY, Pattern.compile("\\b((by|per|across|each)\\s+(compan(y|ies)|recruiters?|employers?)|(compan(y|ies)|recruiter)[- ]?wise|top\\s+(compan|recruit|employer|hirer))", Pattern.CASE_INSENSITIVE)),
            Map.entry(BATCH, split("batch(es)?|year(s)?")),
            Map.entry(MONTH, Pattern.compile("\\b(month|monthly|trend|over time|per month|timeline|month[- ]?on[- ]?month)\\b", Pattern.CASE_INSENSITIVE)),
            Map.entry(STATUS, split("status(es)?")),
            Map.entry(TEST, split("tests?")),
            Map.entry(ROUND, split("rounds?|stages? of selection")),
            Map.entry(JOB, split("jobs?|roles?")),
            Map.entry(TIER, split("tiers?")),
            Map.entry(DIFFICULTY, split("difficulty|levels?")),
            Map.entry(TYPE, split("types?|kinds?")),
            Map.entry(ANNOUNCEMENT, split("announcements?|notices?")));

    private static Pattern split(String noun) {
        return Pattern.compile("\\b((by|per|across|each|every)\\s+(" + noun + ")\\b|(" + noun + ")[- ]?wise\\b)", Pattern.CASE_INSENSITIVE);
    }

    static boolean fits(String title, String metric, Unit unit, Dimension dim) {
        if (COMPARISON.matcher(title).find()) return false;
        for (Map.Entry<Pattern, Set<String>> e : OWNED.entrySet()) {
            if (!e.getValue().contains(metric) && e.getKey().matcher(title).find()) return false;
        }
        if (unit == Unit.COUNT && (RATE.matcher(title).find() || PERCENT_SIGN.matcher(title).find())) return false;
        for (Map.Entry<Dimension, Pattern> e : SPLIT.entrySet()) {
            if (e.getKey() != dim && e.getValue().matcher(title).find()) {
                // a CTC band chart may say "package"; a funnel shows stages; a list of students may say "branch" in its scope
                if (dim == STUDENT || dim == STAGE) continue;
                return false;
            }
        }
        return true;
    }
}
