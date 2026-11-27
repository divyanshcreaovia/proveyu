package com.proveyu.cxo.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.MetricInfo;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.proveyu.cxo.domain.CxoModel.Dimension.*;
import static com.proveyu.cxo.domain.CxoModel.WidgetType.*;

/**
 * Turns a question into a dashboard spec. With an AI key the model plans it from the metric catalog; without one (or when the model
 * fails) a keyword planner answers. Either way the result goes through {@link SpecSanitizer}.
 */
@Component
@RequiredArgsConstructor
public class DashboardPlanner {

    /** @param cached true when the plan came from the AI answer cache (no model call) */
    public record Plan(DashboardSpec spec, String source, boolean cached) {}

    private final CxoAiClient ai;
    private final MetricCatalog catalog;
    private final SpecSanitizer sanitizer;
    private final ObjectMapper json;
    private final CxoAiCache cache;
    private final ScopeEnforcer scope;

    /**
     * The plan for a question: from the AI answer cache when the same question (on the same dashboard) was asked before, else from the
     * model (and cached), else from the keyword planner.
     */
    public Plan plan(UUID collegeId, String prompt, DashboardSpec current, CollegeRef ref) {
        Plan p = planned(collegeId, prompt, current, ref);
        // the years named in the question are applied here, to every plan whoever made it (model, cache or keywords)
        return new Plan(scope.apply(p.spec(), prompt, ref), p.source(), p.cached());
    }

    private Plan planned(UUID collegeId, String prompt, DashboardSpec current, CollegeRef ref) {
        boolean refining = current != null && current.widgets() != null && !current.widgets().isEmpty();
        String key = CxoAiCache.key("plan-v7", CxoAiCache.normalize(prompt), refining ? write(current) : "");
        Optional<DashboardSpec> hit = cache.get(collegeId, CxoAiCache.Kind.PLAN, key).flatMap(this::read).map(s -> sanitizer.clean(s, ref));
        if (hit.isPresent() && !hit.get().widgets().isEmpty()) return new Plan(filled(hit.get(), prompt, refining, ref), "AI", true);

        Optional<JsonNode> reply = ai.json(systemPrompt(ref), userPrompt(prompt, current), 0.2);
        if (reply.isPresent()) {
            if (refining) newFirst(reply.get(), current);
            DashboardSpec spec = sanitizer.fromJson(reply.get(), ref);
            if (!spec.widgets().isEmpty()) {
                cache.put(collegeId, CxoAiCache.Kind.PLAN, key, write(spec));
                return new Plan(filled(spec, prompt, refining, ref), "AI", false);
            }
        }
        DashboardSpec rules = sanitizer.clean(refining ? refine(prompt, sanitizer.clean(current, ref), ref) : rules(prompt, ref), ref);
        return new Plan(timeCompare(prompt) ? comparable(rules, ref) : rules, "RULES", false);
    }

    static final int MIN_WIDGETS = 6;

    /**
     * In a refinement the widgets the user just asked for go first, so that when the dashboard is full (at most
     * {@link SpecSanitizer#MAX_WIDGETS}) the oldest charts make room, not the new request. Order is otherwise kept.
     */
    static void newFirst(JsonNode reply, DashboardSpec current) {
        if (!(reply.path("widgets") instanceof com.fasterxml.jackson.databind.node.ArrayNode arr)) return;
        Set<String> had = new java.util.HashSet<>();
        for (WidgetSpec w : current.widgets()) had.add(w.metric() + "|" + w.dimension());
        List<JsonNode> fresh = new ArrayList<>(), old = new ArrayList<>();
        for (JsonNode w : arr) {
            String k = w.path("metric").asText("").trim().toLowerCase(Locale.ROOT) + "|" + w.path("dimension").asText("").trim().toUpperCase(Locale.ROOT);
            (had.contains(k) ? old : fresh).add(w);
        }
        arr.removeAll();
        fresh.forEach(arr::add);
        old.forEach(arr::add);
    }

    /**
     * A model answer that came out thin (it repeated a chart, or picked very few) is topped up with the keyword planner's widgets for
     * the same question: KPI tiles up to four, then charts it does not already have. A refinement is left exactly as the model made it.
     */
    private DashboardSpec filled(DashboardSpec spec, String prompt, boolean refining, CollegeRef ref) {
        if (!refining && !PERIOD.matcher(prompt).find()) spec = withoutDates(spec); // no period asked: the model must not invent one
        if (timeCompare(prompt)) spec = comparable(spec, ref);
        if (refining || spec.widgets().size() >= MIN_WIDGETS) return spec;
        List<WidgetSpec> out = new ArrayList<>(spec.widgets());
        long kpis = out.stream().filter(w -> w.type() == KPI).count();
        for (WidgetSpec w : sanitizer.clean(rules(prompt, ref), ref).widgets()) {
            if (out.size() >= SpecSanitizer.MAX_WIDGETS) break;
            boolean dup = out.stream().anyMatch(x -> x.metric().equals(w.metric()) && x.dimension() == w.dimension());
            if (dup || (w.type() == KPI && kpis >= 4)) continue;
            if (w.type() == KPI) kpis++;
            out.add(w);
        }
        return sanitizer.clean(new DashboardSpec(spec.title(), spec.summary(), out), ref);
    }

    /** Words that name a time period, in English and Hinglish. */
    static final Pattern PERIOD = Pattern.compile("\\b(20\\d{2}|years?|yearly|annual|months?|monthly|quarters?|q[1-4]|weeks?|weekly|days?|today|yesterday|"
            + "since|last|past|recent|recently|season|semester|ytd|trend|trends|over time|timeline|jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|"
            + "january|february|march|april|june|july|august|september|october|november|december|saal|mahina|mahine|hafta|hafte|aaj|kal|pichhle|pichle)\\b",
            Pattern.CASE_INSENSITIVE);

    /** "vs", "compare" with a time word: "male vs female" is a comparison of groups, not of years. */
    static boolean timeCompare(String prompt) {
        return COMPARE.matcher(prompt).find() && TIME_WORDS.matcher(prompt).find();
    }

    private static final Pattern TIME_WORDS = Pattern.compile("\\b(20\\d{2}|years?|yearly|batch(es)?|saal|sal|growth|grew|improve\\w*|badha|ghata|month(s|ly)?|quarter\\w*|season|yoy)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Questions that compare years ("vs last year", "pichhle saal se", "growth"). */
    static final Pattern COMPARE = Pattern.compile("\\b(vs\\.?|versus|compare|compared|comparison|last year|previous year|year on year|"
            + "year-on-year|yoy|growth|grew|improve|improved|pichhle saal|pichle saal|pichhla saal|pichla saal|se compare|badha|ghata)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * For a comparison question, KPI tiles without a batch or a period get one, so each tile can show its change: this year's
     * graduating batch (compared with the one before) for student-based numbers, else this calendar year so far (compared with the same months last year).
     */
    private DashboardSpec comparable(DashboardSpec spec, CollegeRef ref) {
        // "this year" in placements is the batch graduating this year (or the last one that already graduated), not a batch still
        // being placed; it is compared only when the batch before it exists
        int year = LocalDate.now().getYear();
        Integer latest = ref.batches().stream().filter(b -> b <= year).max(Integer::compare).filter(b -> ref.batches().contains(b - 1)).orElse(null);
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);
        List<WidgetSpec> kpis = new ArrayList<>(), charts = new ArrayList<>();
        for (WidgetSpec w : spec.widgets()) {
            if (w.type() != KPI) { charts.add(w); continue; }
            MetricCatalog.Def d = catalog.find(w.metric()).orElse(null);
            Integer b = w.batchYear();
            if (b != null && !ref.batches().contains(b - 1) && ref.batches().contains(b + 1)) {
                // "2026 vs 2027" with no 2025: show 2027 so the tile can be compared with 2026
                kpis.add(new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(), b + 1, w.branchCode(), w.from(), w.to(), w.limit(), w.size()));
            } else if (b != null || w.from() != null) {
                kpis.add(w);
            } else if (d != null && d.dateCol() != null) {
                kpis.add(new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(), null, w.branchCode(), yearStart, null, w.limit(), w.size()));
            } else if (d != null && d.batchCol() != null && latest != null) {
                kpis.add(new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(), latest, w.branchCode(), null, null, w.limit(), w.size()));
            } else {
                kpis.add(w);
            }
        }
        // side by side: when no chart compares batches yet, add one for each of the first KPI metrics that can be split by batch,
        // right after the tiles so a full dashboard keeps them
        List<WidgetSpec> sideBySide = new ArrayList<>();
        boolean hasBatchChart = charts.stream().anyMatch(w -> w.dimension() == BATCH);
        if (!hasBatchChart && ref.batches().size() >= 2) {
            for (WidgetSpec k : kpis) {
                if (sideBySide.size() >= 3) break;
                MetricCatalog.Def d = catalog.find(k.metric()).orElse(null);
                if (d == null || !catalog.dimensions(d).contains(BATCH) || sideBySide.stream().anyMatch(x -> x.metric().equals(k.metric()))) continue;
                sideBySide.add(new WidgetSpec(null, d.label() + " by batch", BAR, d.key(), BATCH, null, k.branchCode(), null, null, null, "md"));
            }
        }
        List<WidgetSpec> out = new ArrayList<>(kpis);
        out.addAll(sideBySide);
        out.addAll(charts);
        return sanitizer.clean(new DashboardSpec(spec.title(), spec.summary(), out), ref);
    }

    private static DashboardSpec withoutDates(DashboardSpec spec) {
        return new DashboardSpec(spec.title(), spec.summary(), spec.widgets().stream().map(w -> new WidgetSpec(w.id(), w.title(), w.type(), w.metric(),
                w.dimension(), w.batchYear(), w.branchCode(), null, null, w.limit(), w.size())).toList());
    }

    private String write(DashboardSpec s) {
        try {
            return json.writeValueAsString(s);
        } catch (Exception e) {
            return "";
        }
    }

    private Optional<DashboardSpec> read(String s) {
        try {
            return Optional.of(json.readValue(s, DashboardSpec.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ AI prompt

    private String systemPrompt(CollegeRef ref) {
        StringBuilder metrics = new StringBuilder();
        for (MetricInfo m : catalog.infos()) {
            metrics.append("- ").append(m.key()).append(" (").append(m.unit().name().toLowerCase(Locale.ROOT)).append("): ").append(m.description())
                    .append(". dimensions: ").append(m.dimensions()).append('\n');
        }
        return """
                You design dashboards for the leadership (principal, director, management) of an Indian engineering college, on the
                ProveYu campus placement platform. Answer with ONE JSON object only, no prose:
                {"title": string, "summary": string,
                 "widgets": [{"title": string, "type": "KPI|BAR|COLUMN|LINE|AREA|DONUT|TABLE", "metric": string, "dimension": string,
                              "batchYear": number|null, "branchCode": string|null, "from": "YYYY-MM-DD"|null, "to": "YYYY-MM-DD"|null,
                              "limit": number|null, "size": "sm|md|lg"}]}

                Metrics you may use (use the key exactly; use only a dimension listed for that metric):
                %s
                Rules:
                - Start with exactly 4 KPI widgets (dimension NONE, size sm) that answer the question at a glance; put the single most
                  important number first. Then 5 to 7 charts that each add something new (a trend, a ranking, a split, the funnel).
                - LINE or AREA only with dimension MONTH (trends). DONUT only for count metrics over a few categories (STATUS, TYPE, CTC_BAND, DIFFICULTY, TIER),
                  never for rates, averages or the funnel.
                  BAR for rankings (COMPANY, BRANCH, TEST, JOB, ANNOUNCEMENT); size lg for wide charts, md otherwise. TABLE when the user asks
                  for a list; names of students only with unplaced_list (dimension STUDENT, type TABLE), and only when asked for names or a list.
                - A MONTH chart covers at most the last 24 months; leave from/to empty unless the user names a period.
                - Filters only when the user asks: batchYear is the graduating year the user names (use it even when it is not one of %s: the system then shows no data), branchCode one of %s. Dates relative to today, %s.
                - Short business titles (max 6 words), no metric keys in titles. A title must say exactly what the metric and
                  dimension show (e.g. companies by STATUS is "Recruiters by MoU status", not "Top recruiters"). summary: one sentence on what the dashboard shows.
                - Never use the same metric with the same dimension and filters twice. One widget shows one metric: never title it as a
                  comparison of two metrics ("X vs Y").
                - Year on year: a KPI with batchYear (or a date range of up to a year) automatically shows the change against the previous
                  batch (or the same period last year). For "this year vs last year" use such KPIs plus charts by BATCH (side by side).
                - If the question is not about the college's placements, students, tests or interviews, build the placement overview
                  and say in summary that this portal answers questions about placement data.
                - If something cannot be answered with these metrics, build the closest useful dashboard and say what is missing in summary.
                - When a current dashboard is given, change it as asked and keep the rest. At most 12 widgets: when adding to a full
                  dashboard, drop the charts least related to the request.
                """.formatted(metrics, ref.batches(), ref.branches(), LocalDate.now());
    }

    private String userPrompt(String prompt, DashboardSpec current) {
        if (current == null || current.widgets() == null || current.widgets().isEmpty()) return prompt;
        try {
            return "Current dashboard:\n" + json.writeValueAsString(current) + "\n\nChange request: " + prompt;
        } catch (Exception e) {
            return prompt;
        }
    }

    // ------------------------------------------------------------------ keyword planner (no AI)

    private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2})\\b");

    DashboardSpec rules(String prompt, CollegeRef ref) {
        String p = " " + prompt.toLowerCase(Locale.ROOT) + " ";
        Integer batch = timeCompare(prompt) ? latestBatch(p, ref) : batch(p, ref);
        String branch = branch(prompt, ref);
        List<WidgetSpec> w = new ArrayList<>();
        Set<String> topics = new LinkedHashSet<>();
        if (has(p, "ctc", "package", "salary", "lpa", " pay", "tankhwah", "kamai", "kitna mila", "median")) topics.add("ctc");
        if (has(p, "branch", "department", "stream", "vibhag")) topics.add("branch");
        if (has(p, "company", "companies", "recruiter", "employer", "kampani", "kampaniyan")) topics.add("company");
        if (has(p, "trend", "month", "over time", "growth", "timeline", "mahine", "mahina")) topics.add("trend");
        if (has(p, "drive")) topics.add("drive");
        if (has(p, "application", "funnel", "pipeline", "shortlist", "selection")) topics.add("funnel");
        if (has(p, " test", "mock", " exam", "assessment", "aptitude")) topics.add("tests");
        if (has(p, "ai interview", "mock interview", "interview practice", "ai mock")) topics.add("ai");
        else if (has(p, "interview")) topics.add("interviews");
        if (has(p, "off-campus", "off campus", "offcampus")) topics.add("offcampus");
        if (has(p, "selection rate", "conversion", "success rate", "hit rate")) topics.add("selection");
        if (has(p, "unplaced", "not placed", "placed nahi", "nahi hue", "bina offer", "placement nahi", "job nahi", "nahi mili", "not yet placed", "yet to be placed", "without offer", "without an offer", "jobless", "remaining")) topics.add("unplaced");
        if (has(p, "mou", "partner", "tie-up", "tie up", "tieup")) topics.add("partners");
        if (topics.contains("unplaced") && has(p, "list", "name", "naam", "who ", "kaun", "which students", "students who")) topics.add("unplacedList");
        if (has(p, " round", "aptitude", "coding round", "technical round", "hr round", "drop off", "drop-off", "dropoff")) topics.add("rounds");
        if (has(p, "accept", "decline", "declined", "acceptance", "joined", "reneg")) topics.add("acceptance");
        if (has(p, "announcement", "notice", "circular", "broadcast", " read ", "opened")) topics.add("announcements");
        if (has(p, "proctor", "cheat", "malpractice", "integrity", "flag", "tab switch")) topics.add("proctoring");
        if (has(p, "code ground", "codeground", "coding practice", "practising code", "practicing code", "problems solved", "code submission", "programming language", "leetcode", " dsa ", "coders", "coding activity", "code practice")) topics.add("code");
        if (has(p, "cgpa", "academic performance", "grades")) topics.add("cgpa");
        if (has(p, "new placement", "newly placed", "recently placed", "placed this month", "placed so far", "placed per month", "placements per month")) topics.add("newPlaced");
        if (has(p, "multiple offer", "more than one offer", "several offers", "multi offer", "multi-offer", "one offer")) topics.add("multiOffer");
        if (has(p, "never applied", "not applied", "no application", "zero application", "haven't applied", "have not applied", "applied nahi", "apply nahi")) topics.add("noApply");
        if (has(p, "document", "verification", "verified")) topics.add("documents");
        if (has(p, "gender", "female", "women", "girls", " male ", "boys")) topics.add("gender");
        boolean onTopic = !topics.isEmpty() || has(p, "placement", "placed", "student", "offer", "college", "batch", "overview", "summary",
                "dashboard", "performance", "report", "kpi", "progress", "status", "naukri", "job");

        // KPIs
        if (topics.contains("newPlaced")) {
            w.add(kpi("Students placed", "new_placements", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
            w.add(kpi("Offers", "offers", batch, branch));
            w.add(kpi("Unplaced students", "unplaced_students", batch, branch));
        } else if (topics.contains("multiOffer")) {
            w.add(kpi("Students with several offers", "multi_offer_students", batch, branch));
            w.add(kpi("Placed", "placed_students", batch, branch));
            w.add(kpi("Offers", "offers", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
        } else if (topics.contains("noApply")) {
            w.add(kpi("Students who never applied", "zero_application_students", batch, branch));
            w.add(kpi("Unplaced students", "unplaced_students", batch, branch));
            w.add(kpi("Applications", "applications", batch, branch));
            w.add(kpi("Students", "students_total", batch, branch));
        } else if (topics.contains("documents")) {
            w.add(kpi("Student documents", "student_documents", batch, branch));
            w.add(kpi("Students", "students_total", batch, branch));
        } else if (topics.contains("code")) {
            w.add(kpi("Students practising code", "code_active_coders", batch, branch));
            w.add(kpi("Code submissions", "code_submissions", batch, branch));
            w.add(kpi("Accepted rate", "code_accept_rate", batch, branch));
            w.add(kpi("Code problems solved", "code_problems_solved", batch, branch));
        } else if (topics.contains("cgpa")) {
            w.add(kpi("Average CGPA", "avg_cgpa", batch, branch));
            w.add(kpi("Students", "students_total", batch, branch));
            w.add(kpi("Placed", "placed_students", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
        } else if (topics.contains("gender")) {
            w.add(kpi("Students", "students_total", batch, branch));
            w.add(kpi("Placed", "placed_students", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
            w.add(kpi("Average CTC", "avg_ctc", batch, branch));
        } else if (topics.contains("drive") && topics.size() == 1) {
            w.add(kpi("Placement drives", "drives", null, null));
            w.add(kpi("Package on offer", "avg_drive_ctc", null, null));
            w.add(kpi("Applications", "applications", batch, branch));
            w.add(kpi("Selection rate", "selection_rate", batch, branch));
        } else if (topics.contains("proctoring")) {
            w.add(kpi("Proctoring flags", "proctoring_flags", batch, branch));
            w.add(kpi("Flagged attempts", "flagged_attempts", batch, branch));
            w.add(kpi("Test attempts", "mock_test_attempts", batch, branch));
        } else if (topics.contains("ctc")) {
            w.add(kpi("Average CTC", "avg_ctc", batch, branch));
            w.add(kpi("Highest CTC", "highest_ctc", batch, branch));
            w.add(kpi("Median CTC", "median_ctc", batch, branch));
            w.add(kpi("Lowest CTC", "lowest_ctc", batch, branch));
        } else if (topics.contains("tests")) {
            w.add(kpi("Test attempts", "mock_test_attempts", batch, branch));
            w.add(kpi("Average score", "mock_test_avg_score", batch, branch));
            w.add(kpi("Students", "students_total", batch, branch));
        } else if (topics.contains("ai")) {
            w.add(kpi("AI interviews", "ai_interviews", batch, branch));
            w.add(kpi("Average AI score", "ai_interview_score", batch, branch));
            w.add(kpi("Students", "students_total", batch, branch));
        } else if (topics.contains("rounds")) {
            w.add(kpi("Round pass rate", "round_pass_rate", batch, branch));
            w.add(kpi("Round results", "round_results", batch, branch));
            w.add(kpi("Selection rate", "selection_rate", batch, branch));
            w.add(kpi("Applications", "applications", batch, branch));
        } else if (topics.contains("acceptance")) {
            w.add(kpi("Offer acceptance rate", "offer_acceptance_rate", batch, branch));
            w.add(kpi("Offers", "offers", batch, branch));
            w.add(kpi("Placed", "placed_students", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
        } else if (topics.contains("announcements")) {
            w.add(kpi("Announcements sent", "announcements", null, null));
            w.add(kpi("Read rate", "announcement_read_rate", batch, branch));
            w.add(kpi("Students", "students_total", batch, branch));
        } else if (topics.contains("unplaced")) {
            w.add(kpi("Unplaced students", "unplaced_students", batch, branch));
            w.add(kpi("Placed", "placed_students", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
            w.add(kpi("Students", "students_total", batch, branch));
        } else if (topics.contains("selection")) {
            w.add(kpi("Selection rate", "selection_rate", batch, branch));
            w.add(kpi("Applications", "applications", batch, branch));
            w.add(kpi("Offers", "offers", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
        } else if (topics.contains("company") && topics.size() == 1) {
            w.add(kpi("Offers", "offers", batch, branch));
            w.add(kpi("Companies that hired", "companies_hired", batch, branch));
            w.add(kpi("Placed", "placed_students", batch, branch));
            w.add(kpi("Average CTC", "avg_ctc", batch, branch));
        } else if (topics.contains("partners")) {
            w.add(kpi("Companies", "companies", null, null));
            w.add(kpi("Companies that hired", "companies_hired", batch, branch));
            w.add(kpi("Drives", "drives", null, null));
            w.add(kpi("Offers", "offers", batch, branch));
            w.add(kpi("Average CTC", "avg_ctc", batch, branch));
        } else {
            w.add(kpi("Students", "students_total", batch, branch));
            w.add(kpi("Placed", "placed_students", batch, branch));
            w.add(kpi("Placement rate", "placement_rate", batch, branch));
            w.add(kpi("Average CTC", "avg_ctc", batch, branch));
        }

        // charts
        if (topics.contains("code")) {
            w.add(chart("Code submissions per month", AREA, "code_submissions", MONTH, batch, branch, "lg"));
            w.add(chart("Submissions by language", DONUT, "code_submissions", LANGUAGE, batch, branch, "md"));
            w.add(chart("Students practising by branch", BAR, "code_active_coders", BRANCH, batch, branch, "md"));
            w.add(chart("Problems solved by branch", BAR, "code_problems_solved", BRANCH, batch, branch, "md"));
            w.add(chart("Accepted rate by branch", BAR, "code_accept_rate", BRANCH, batch, branch, "md"));
        }
        if (topics.contains("newPlaced")) {
            w.add(chart("Students placed per month", COLUMN, "new_placements", MONTH, batch, branch, "lg"));
            w.add(chart("Students placed by branch", BAR, "new_placements", BRANCH, batch, branch, "md"));
        }
        if (topics.contains("multiOffer")) w.add(chart("Students with several offers by branch", BAR, "multi_offer_students", BRANCH, batch, branch, "md"));
        if (topics.contains("noApply")) {
            w.add(chart("Never applied by branch", BAR, "zero_application_students", BRANCH, batch, branch, "md"));
            w.add(chart("Never applied by CGPA band", COLUMN, "zero_application_students", CGPA_BAND, batch, branch, "md"));
        }
        if (topics.contains("documents")) {
            w.add(chart("Documents by status", DONUT, "student_documents", STATUS, batch, branch, "md"));
            w.add(chart("Documents by type", BAR, "student_documents", TYPE, batch, branch, "md"));
        }
        if (topics.contains("cgpa")) {
            w.add(chart("Placement rate by CGPA band", COLUMN, "placement_rate", CGPA_BAND, batch, branch, "md"));
            w.add(chart("Average CGPA by branch", BAR, "avg_cgpa", BRANCH, batch, branch, "md"));
            w.add(chart("Average CGPA by batch", COLUMN, "avg_cgpa", BATCH, null, branch, "md"));
        }
        if (topics.contains("gender")) {
            w.add(chart("Students by gender", DONUT, "students_total", GENDER, batch, branch, "md"));
            w.add(chart("Placement rate by gender", BAR, "placement_rate", GENDER, batch, branch, "md"));
            w.add(chart("Average CTC by gender", BAR, "avg_ctc", GENDER, batch, branch, "md"));
        }
        if (topics.contains("ctc")) {
            w.add(chart("Offers by package", DONUT, "offers_by_ctc", CTC_BAND, batch, branch, "md"));
            w.add(chart("Average CTC by branch", BAR, "avg_ctc", BRANCH, batch, branch, "md"));
            w.add(chart("Highest CTC by company", BAR, "highest_ctc", COMPANY, batch, branch, "lg"));
        }
        if (topics.contains("branch")) {
            w.add(chart("Placement rate by branch", BAR, "placement_rate", BRANCH, batch, branch, "md"));
            w.add(chart("Unplaced students by branch", BAR, "unplaced_students", BRANCH, batch, branch, "md"));
            w.add(chart("Placed students by branch", BAR, "placed_students", BRANCH, batch, branch, "md"));
            w.add(chart("Average CTC by branch", BAR, "avg_ctc", BRANCH, batch, branch, "md"));
        }
        if (topics.contains("company")) {
            w.add(chart("Top recruiters by offers", BAR, "offers", COMPANY, batch, branch, "lg"));
            w.add(chart("Highest CTC by company", BAR, "highest_ctc", COMPANY, batch, branch, "md"));
            w.add(chart("Selection rate by company", BAR, "selection_rate", COMPANY, batch, branch, "md"));
        }
        if (topics.contains("trend")) w.add(chart("Offers per month", AREA, "offers", MONTH, batch, branch, "lg"));
        if (topics.contains("drive")) {
            w.add(chart("Drives by status", DONUT, "drives", STATUS, null, null, "md"));
            w.add(chart("Drives per month", COLUMN, "drives", MONTH, null, null, "md"));
        }
        if (topics.contains("funnel")) {
            w.add(chart("Placement funnel", BAR, "placement_funnel", STAGE, batch, branch, "md"));
            w.add(chart("Applications by status", DONUT, "applications", STATUS, batch, branch, "md"));
        }
        if (topics.contains("tests")) {
            w.add(chart("Test attempts per month", LINE, "mock_test_attempts", MONTH, batch, branch, "md"));
            w.add(chart("Average score by test", BAR, "mock_test_avg_score", TEST, batch, branch, "md"));
            w.add(chart("Average score by branch", BAR, "mock_test_avg_score", BRANCH, batch, branch, "lg"));
        }
        if (topics.contains("ai")) {
            w.add(chart("AI interviews per month", AREA, "ai_interviews", MONTH, batch, branch, "md"));
            w.add(chart("AI score by level", BAR, "ai_interview_score", DIFFICULTY, batch, branch, "md"));
        }
        if (topics.contains("interviews")) w.add(chart("Interviews by status", DONUT, "interviews", STATUS, null, null, "md"));
        if (topics.contains("unplaced")) w.add(chart("Unplaced students by branch", BAR, "unplaced_students", BRANCH, batch, branch, "md"));
        if (topics.contains("unplacedList")) w.add(chart("Unplaced students", TABLE, "unplaced_list", STUDENT, batch, branch, "lg"));
        if (topics.contains("rounds")) {
            w.add(chart("Pass rate by round", BAR, "round_pass_rate", ROUND, batch, branch, "md"));
            w.add(chart("Round results", DONUT, "round_results", STATUS, batch, branch, "md"));
            w.add(chart("Pass rate by company", BAR, "round_pass_rate", COMPANY, batch, branch, "lg"));
        }
        if (topics.contains("acceptance")) {
            w.add(chart("Acceptance rate by company", BAR, "offer_acceptance_rate", COMPANY, batch, branch, "md"));
            w.add(chart("Offers by status", DONUT, "offers", STATUS, batch, branch, "md"));
        }
        if (topics.contains("announcements")) {
            w.add(chart("Read rate by announcement", BAR, "announcement_read_rate", ANNOUNCEMENT, batch, branch, "lg"));
            w.add(chart("Read rate by branch", BAR, "announcement_read_rate", BRANCH, batch, branch, "md"));
            w.add(chart("Announcements per month", COLUMN, "announcements", MONTH, null, null, "md"));
        }
        if (topics.contains("proctoring")) {
            w.add(chart("Flags by type", DONUT, "proctoring_flags", TYPE, batch, branch, "md"));
            w.add(chart("Flags by test", BAR, "proctoring_flags", TEST, batch, branch, "md"));
            w.add(chart("Flagged attempts by branch", BAR, "flagged_attempts", BRANCH, batch, branch, "lg"));
        }
        if (topics.contains("selection")) {
            w.add(chart("Selection rate by company", BAR, "selection_rate", COMPANY, batch, branch, "lg"));
            w.add(chart("Selection rate by branch", BAR, "selection_rate", BRANCH, batch, branch, "md"));
        }
        if (topics.contains("partners")) w.add(chart("Recruiters by MoU status", DONUT, "companies", STATUS, null, null, "md"));
        if (topics.contains("offcampus")) {
            w.add(chart("Off-campus applications", DONUT, "offcampus_applications", STATUS, batch, branch, "md"));
            w.add(chart("Applications by job", BAR, "offcampus_applications", JOB, batch, branch, "md"));
        }

        if (w.size() <= 5) { // nothing specific (or a single topic): a full placement overview
            w.add(chart("Offers per month", AREA, "offers", MONTH, batch, branch, "lg"));
            w.add(chart("Placement rate by branch", COLUMN, "placement_rate", BRANCH, batch, branch, "md"));
            w.add(chart("Placement funnel", BAR, "placement_funnel", STAGE, batch, branch, "md"));
            w.add(chart("Top recruiters by offers", BAR, "offers", COMPANY, batch, branch, "lg"));
            w.add(chart("Offers by package", DONUT, "offers_by_ctc", CTC_BAND, batch, branch, "md"));
            w.add(chart("Average CTC by branch", COLUMN, "avg_ctc", BRANCH, batch, branch, "md"));
            w.add(chart("Applications by status", DONUT, "applications", STATUS, batch, branch, "md"));
        }
        String scope = (batch != null ? " · batch " + batch : "") + (branch != null ? " · " + branch : "");
        String title = topics.size() == 1 && topics.contains("branch") ? "Branch-wise placement" : topics.size() == 1 && topics.contains("company") ? "Top recruiters" : topics.contains("newPlaced") ? "Placements so far" : topics.contains("multiOffer") ? "Students with several offers" : topics.contains("noApply") ? "Students who never applied" : topics.contains("documents") ? "Document verification" : topics.contains("code") ? "Code ground practice" : topics.contains("cgpa") ? "Academic profile" : topics.contains("gender") ? "Placement by gender"
                : topics.contains("rounds") ? "Selection rounds" : topics.contains("proctoring") ? "Test integrity"
                : topics.contains("announcements") ? "Announcement reach" : topics.contains("acceptance") ? "Offer acceptance"
                : topics.contains("unplacedList") ? "Unplaced students" : topics.contains("ctc") ? "Packages and CTC" : topics.contains("tests") ? "Test performance"
                : topics.contains("ai") ? "AI interview practice" : topics.contains("drive") ? "Drives overview" : "Placement overview";
        String summary = onTopic ? "Built from your question using the standard placement metrics."
                : "This portal answers questions about your college's placement data, so here is the placement overview.";
        return new DashboardSpec(title + scope, summary, w);
    }

    /** Follow-up without AI: apply a batch / branch filter or switch the trend charts, keep the rest. */
    private DashboardSpec refine(String prompt, DashboardSpec current, CollegeRef ref) {
        String p = " " + prompt.toLowerCase(Locale.ROOT) + " ";
        String branch = branch(prompt, ref);
        if (timeCompare(prompt)) {
            // a comparison: tiles move to the later batch named (charts stay as they are; side-by-side charts are added afterwards)
            Integer later = latestBatch(p, ref);
            List<WidgetSpec> tiles = current.widgets().stream().filter(w -> w.type() == KPI).map(w -> later == null ? w
                    : new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(), later, branch != null ? branch : w.branchCode(),
                            w.from(), w.to(), w.limit(), w.size())).toList();
            // charts for what the question names ("offers and packages"), new ones first so a full dashboard keeps them
            List<WidgetSpec> asked = new ArrayList<>();
            for (WidgetSpec w : rules(prompt, ref).widgets()) {
                boolean dup = current.widgets().stream().anyMatch(x -> x.metric().equals(w.metric()) && x.dimension() == w.dimension())
                        || asked.stream().anyMatch(x -> x.metric().equals(w.metric()) && x.dimension() == w.dimension());
                if (!dup && w.type() != KPI) asked.add(w);
            }
            List<WidgetSpec> out = new ArrayList<>(tiles);
            out.addAll(asked);
            out.addAll(current.widgets().stream().filter(w -> w.type() != KPI).toList());
            return new DashboardSpec(baseTitle(current.title()) + (later != null ? " · batch " + later : ""), current.summary(), out);
        }
        Integer batch = batch(p, ref);
        if (batch == null && branch == null) {
            DashboardSpec extra = rules(prompt, ref);
            List<WidgetSpec> added = new ArrayList<>();
            for (WidgetSpec w : extra.widgets()) {
                boolean dup = current.widgets().stream().anyMatch(x -> x.metric().equals(w.metric()) && x.dimension() == w.dimension());
                if (!dup && w.type() != KPI) added.add(w);
            }
            // the new charts first, so a full dashboard drops its oldest charts rather than what was just asked for
            List<WidgetSpec> merged = new ArrayList<>(current.widgets().stream().filter(x -> x.type() == KPI).toList());
            merged.addAll(added);
            merged.addAll(current.widgets().stream().filter(x -> x.type() != KPI).toList());
            return new DashboardSpec(current.title(), current.summary(), merged);
        }
        List<WidgetSpec> out = current.widgets().stream().map(w -> new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(),
                batch != null ? batch : w.batchYear(), branch != null ? branch : w.branchCode(), w.from(), w.to(), w.limit(), w.size())).toList();
        String scope = (batch != null ? " · batch " + batch : "") + (branch != null ? " · " + branch : "");
        return new DashboardSpec(baseTitle(current.title()) + scope, current.summary(), out);
    }

    /** A title without the " · batch 2026 · COMP" scope a previous follow-up added, so scopes never pile up. */
    static String baseTitle(String title) {
        return title == null ? "Placement overview" : title.replaceAll("(\\s·\\s(batch \\d{4}|[A-Z]{2,8}))+$", "");
    }

    /** The latest batch year named in a question (for "2026 vs 2027", 2027). */
    private static Integer latestBatch(String p, CollegeRef ref) {
        Matcher m = YEAR.matcher(p);
        Integer best = null;
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (ref.batches().contains(y) && (best == null || y > best)) best = y;
        }
        return best;
    }

    // ------------------------------------------------------------------ helpers

    private static WidgetSpec kpi(String title, String metric, Integer batch, String branch) {
        return new WidgetSpec(null, title, KPI, metric, NONE, batch, branch, null, null, null, "sm");
    }

    private static WidgetSpec chart(String title, WidgetType type, String metric, Dimension dim, Integer batch, String branch, String size) {
        return new WidgetSpec(null, title, type, metric, dim, batch, branch, null, null, null, size);
    }

    private static boolean has(String p, String... words) {
        for (String w : words) if (p.contains(w)) return true;
        return false;
    }

    private static Integer batch(String p, CollegeRef ref) {
        Matcher m = YEAR.matcher(p);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (ref.batches().contains(y)) return y;
        }
        return null;
    }

    private static String branch(String prompt, CollegeRef ref) {
        for (String b : ref.branches()) {
            if (Pattern.compile("\\b" + Pattern.quote(b) + "\\b", Pattern.CASE_INSENSITIVE).matcher(prompt).find()) return b;
        }
        return null;
    }
}
