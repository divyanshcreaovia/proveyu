package com.proveyu.cxo.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoDashboard;
import com.proveyu.cxo.domain.CxoModel.*;
import com.proveyu.cxo.infrastructure.CxoDashboardRepository;
import com.proveyu.cxo.infrastructure.CxoMemberRepository;
import com.proveyu.cxo.security.CxoContext;
import com.proveyu.shared.error.DomainException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The CXO portal: build a dashboard from a question (streamed live), run a spec for fresh numbers, and keep saved dashboards.
 * <p>
 * Stream events, in order: {@code status} (progress steps), {@code plan} (title + empty widget slots), {@code widget} (one per widget,
 * with its data), {@code insight}, {@code done} (final spec), or {@code error}.
 */
@Slf4j
@Service
public class CxoDashboardService {

    private static final int MAX_SAVED = 50;

    private final DashboardPlanner planner;
    private final SpecSanitizer sanitizer;
    private final QueryEngine engine;
    private final InsightWriter insights;
    private final MetricCatalog catalog;
    private final CxoAiClient ai;
    private final CxoDashboardRepository dashboards;
    private final CxoMemberRepository members;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final long paceMs;
    private final ReportExporter exporter;
    /** Users with a dashboard being built right now: one at a time per user. */
    private final Set<UUID> building = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool;

    public CxoDashboardService(DashboardPlanner planner, SpecSanitizer sanitizer, QueryEngine engine, InsightWriter insights, MetricCatalog catalog,
                               CxoAiClient ai, CxoDashboardRepository dashboards, CxoMemberRepository members, JdbcTemplate jdbc, ObjectMapper json,
                               ReportExporter exporter, @Value("${cxo.stream.pace-ms:220}") long paceMs, @Value("${cxo.stream.max-parallel:8}") int maxParallel) {
        this.exporter = exporter;
        this.planner = planner;
        this.sanitizer = sanitizer;
        this.engine = engine;
        this.insights = insights;
        this.catalog = catalog;
        this.ai = ai;
        this.dashboards = dashboards;
        this.members = members;
        this.jdbc = jdbc;
        this.json = json;
        this.paceMs = Math.max(0, Math.min(paceMs, 1500));
        AtomicInteger n = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(Math.max(1, maxParallel), Math.max(1, maxParallel), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32), r -> {
            Thread t = new Thread(r, "cxo-stream-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------ profile + catalog

    public Me me(CxoContext ctx) {
        members.findById(ctx.memberId()).ifPresent(m -> { m.setLastLoginAt(Instant.now()); members.save(m); });
        return new Me(ctx.userId(), ctx.fullName(), ctx.email(), ctx.designation(), ctx.collegeId(), ctx.collegeName());
    }

    public Catalog catalog(CxoContext ctx) {
        CollegeRef ref = ref(ctx);
        List<Integer> batches = ref.batches();
        int now = java.time.LocalDate.now().getYear();
        Integer current = batches.isEmpty() ? null : batches.contains(now) ? Integer.valueOf(now)
                : batches.stream().filter(y -> y >= now).findFirst().orElse(batches.get(batches.size() - 1));
        String season = current == null ? "this year" : "batch " + current;
        String compare = current != null && batches.contains(current - 1) ? "Compare placements of batch " + (current - 1) + " and batch " + current
                : "Compare placement across all years";
        // the fifteen questions a leader asks most; each one is built and checked against the college's data
        List<String> suggestions = List.of(
                "How is our placement season going " + (current == null ? "this year?" : "for batch " + current + "?"),
                "What is the placement status of " + season + "?",
                compare,
                "Placement rate by branch: which branch is behind?",
                "Show the package distribution with average, median and highest CTC",
                "Which companies hired the most of our students?",
                "List the students who are not placed yet",
                "Where do students drop off in the placement funnel?",
                "Which selection round has the lowest pass rate?",
                "Offer acceptance rate by company",
                "Placement rate by CGPA band and by gender",
                "Which students never applied to any drive?",
                "Monthly trend of offers and new placements",
                "How are students doing in mock tests and AI interviews?",
                "Code ground practice: how many students practise and how much?");
        return new Catalog(catalog.infos(), ref.branches(), ref.batches(), suggestions, ai.enabled());
    }

    // ------------------------------------------------------------------ live build

    /**
     * Starts the live build. Problems at this point (busy) are sent as an {@code error} event on the stream: the client asked for
     * text/event-stream, so a JSON error body could not be written.
     */
    public SseEmitter generate(CxoContext ctx, GenerateRequest req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        if (!building.add(ctx.userId())) return failed(emitter, "A dashboard is already being built. Wait for it to finish.");
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> { if (released.compareAndSet(false, true)) building.remove(ctx.userId()); }; // once per build
        try {
            pool.execute(() -> {
                try {
                    stream(ctx, req, emitter, release);
                } finally {
                    release.run();
                }
            });
        } catch (RejectedExecutionException e) {
            release.run();
            return failed(emitter, "The dashboard builder is busy. Try again in a moment.");
        }
        return emitter;
    }

    private SseEmitter failed(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void stream(CxoContext ctx, GenerateRequest req, SseEmitter emitter, Runnable release) {
        long t0 = System.currentTimeMillis();
        String prompt = req.prompt().strip();
        DashboardSpec spec = null;
        String source = "RULES";
        try {
            CollegeRef ref = ref(ctx);
            send(emitter, "status", new Status("understand", ai.enabled() ? "Reading your question" : "Reading your question (rules mode)"));
            send(emitter, "status", new Status("plan", "Choosing the right metrics and charts"));
            DashboardPlanner.Plan plan = planner.plan(ctx.collegeId(), prompt, req.current(), ref);
            if (plan.cached()) send(emitter, "status", new Status("plan", "Found this answer in the cache"));
            spec = plan.spec();
            source = plan.source();
            send(emitter, "plan", spec);
            pause();

            send(emitter, "status", new Status("query", "Querying " + ctx.collegeName() + " data"));
            Map<String, WidgetData> data = new LinkedHashMap<>();
            for (WidgetSpec w : spec.widgets()) {
                WidgetData d = safeRun(w, ctx.collegeId());
                data.put(w.id(), d);
                send(emitter, "widget", d);
                pause();
            }

            send(emitter, "status", new Status("insight", "Writing the key takeaways"));
            send(emitter, "insight", insights.write(ctx.collegeId(), prompt, spec, data));
            release.run(); // free the user before the client sees "done", so an immediate follow-up is accepted
            spec = withoutMissingYears(spec, ref, prompt); // the title must not promise a year that is not on record
            send(emitter, "done", new Done(spec, source, plan.cached(), System.currentTimeMillis() - t0, notices(prompt, spec, data, ref, ctx.collegeName())));
            emitter.complete();
            log(ctx, prompt, spec, plan.cached() ? "CACHE" : source, "OK", null, t0);
        } catch (ClientGone e) {
            log(ctx, prompt, spec, source, "FAILED", "client disconnected", t0);
        } catch (Exception e) {
            log.warn("CXO dashboard failed for college {}: {}", ctx.collegeId(), e.toString());
            release.run();
            try {
                send(emitter, "error", Map.of("message", "Could not build this dashboard. Try rephrasing the question."));
                emitter.complete();
            } catch (Exception ignored) {
                emitter.completeWithError(e);
            }
            log(ctx, prompt, spec, source, "FAILED", e.getMessage(), t0);
        }
    }

    private static final java.util.regex.Pattern YEAR = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b");

    /**
     * What the person should know before reading the numbers: a year they asked about that is not a batch on record (the filter
     * could not be applied, so the board shows the batches that exist, or the dated numbers of that year where there are any), and
     * a board where nothing has been recorded yet.
     */
    static List<String> notices(String prompt, DashboardSpec spec, Map<String, WidgetData> data, CollegeRef ref, String college) {
        List<String> out = new ArrayList<>();
        ScopeEnforcer.Scope scope = ScopeEnforcer.parse(prompt);
        List<Integer> missing = ScopeEnforcer.missing(scope, ref);
        if (scope.any()) {
            out.add("Years: numbers about students (placed, offers, applications, tests...) are for the batch graduating that year; numbers that only have a date "
                    + "(drives, interviews, announcements) are for that calendar year.");
        }
        if (!missing.isEmpty()) {
            boolean side = spec.widgets().stream().anyMatch(w -> w.batches() != null);
            String tail = side ? "left out of the charts; the other years are shown." : "Nothing is shown for it.";
            if (missing.size() > 3) {
                out.add("Years " + missing.get(0) + "–" + missing.get(missing.size() - 1) + " have no batch in " + college + "'s records (on record: "
                        + ScopeEnforcer.list(ref.batches()) + "). " + (side ? "Years without a batch are left out; the others are shown." : "Nothing is shown for them."));
            } else for (int y : missing) {
                out.add("There is no batch " + y + " in " + college + "'s records (on record: " + ScopeEnforcer.list(ref.batches()) + "). "
                        + (side ? "It is " + tail : tail));
            }
        }
        boolean nothing = !data.isEmpty() && data.values().stream().allMatch(d -> (d.value() == null || d.value() == 0)
                && d.values().stream().allMatch(v -> v == null || v == 0));
        if (nothing) out.add("Nothing has been recorded for this question yet, so the numbers below are empty.");
        return out;
    }

    /** The dashboard title without batch years that are not on record ("2025 Batch Placement Overview" -> "Batch Placement Overview"). */
    static DashboardSpec withoutMissingYears(DashboardSpec spec, CollegeRef ref, String prompt) {
        java.util.Set<Integer> asked = new java.util.HashSet<>(ScopeEnforcer.parse(prompt).years());
        String title = spec.title();
        java.util.regex.Matcher m = YEAR.matcher(title);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, ref.batches().contains(Integer.parseInt(m.group(1))) || asked.contains(Integer.parseInt(m.group(1))) ? m.group() : "");
        m.appendTail(out);
        String clean = out.toString().replaceAll("\\s{2,}", " ").replaceAll("^[\\s·\\-–]+|[\\s·\\-–]+$", "").trim();
        if (clean.equals(title)) return spec;
        return new DashboardSpec(clean.isEmpty() ? "Placement overview" : Character.toUpperCase(clean.charAt(0)) + clean.substring(1), spec.summary(), spec.widgets());
    }

    /** Fresh numbers for a spec (auto-refresh of an open dashboard). */
    public List<WidgetData> run(CxoContext ctx, DashboardSpec spec) {
        List<WidgetData> out = new ArrayList<>();
        for (WidgetSpec w : sanitizer.forRun(spec, ref(ctx))) out.add(safeRun(w, ctx.collegeId())); // ids as the client sent them
        return out;
    }

    /** The takeaway for a saved or reopened dashboard: its numbers now, summarized (from the cache while they stay the same). */
    public Insight summary(CxoContext ctx, SummaryRequest req) {
        DashboardSpec clean = new DashboardSpec(req.spec() == null ? null : req.spec().title(), null, sanitizer.forRun(req.spec(), ref(ctx)));
        Map<String, WidgetData> data = new LinkedHashMap<>();
        for (WidgetSpec w : clean.widgets()) data.put(w.id(), safeRun(w, ctx.collegeId()));
        String question = req.prompt() == null || req.prompt().isBlank() ? clean.title() : req.prompt().strip();
        return insights.write(ctx.collegeId(), question, clean, data);
    }

    /** A failing widget shows as empty instead of breaking the whole dashboard. */
    private WidgetData safeRun(WidgetSpec w, UUID collegeId) {
        try {
            return engine.run(w, collegeId);
        } catch (Exception e) {
            log.warn("CXO widget {} ({}) failed: {}", w.id(), w.metric(), e.toString());
            Unit unit = catalog.find(w.metric()).map(MetricCatalog.Def::unit).orElse(Unit.COUNT);
            return new WidgetData(w.id(), unit, null, List.of(), List.of());
        }
    }

    // ------------------------------------------------------------------ drill-down and export

    private static final int DRILL_MAX = 200;

    /** The students behind one bar (or a KPI): the widget is checked against the catalog first, exactly as for live numbers. */
    public Drill drill(CxoContext ctx, DrillRequest req) {
        List<WidgetSpec> ok = sanitizer.forRun(new DashboardSpec("d", null, List.of(req.widget())), ref(ctx));
        if (ok.isEmpty()) throw new DomainException("This widget cannot be listed", HttpStatus.BAD_REQUEST, "BAD_WIDGET");
        log.info("CXO drill-down: college {} metric {} label '{}'", ctx.collegeId(), ok.get(0).metric(), req.label());
        return engine.drill(ok.get(0), req.label(), ctx.collegeId(), DRILL_MAX);
    }

    /** The dashboard on screen as a board report: PDF or PowerPoint. Numbers are fresh; lists of student names are left out. */
    public byte[] export(CxoContext ctx, SummaryRequest req, boolean pptx) {
        DashboardSpec onScreen = req.spec();
        CollegeRef ref = ref(ctx);
        List<WidgetSpec> run = sanitizer.forRun(onScreen, ref);
        if (run.isEmpty()) throw new DomainException("This dashboard has no valid widgets", HttpStatus.BAD_REQUEST, "EMPTY_DASHBOARD");
        Map<String, WidgetData> data = new LinkedHashMap<>();
        for (WidgetSpec w : run) data.put(w.id(), safeRun(w, ctx.collegeId()));
        DashboardSpec spec = new DashboardSpec(onScreen.title(), onScreen.summary(), run);
        Insight insight = null;
        try {
            insight = insights.write(ctx.collegeId(), req.prompt(), spec, data);
        } catch (Exception e) {
            log.warn("Report insight failed: {}", e.toString());
        }
        String scope = req.prompt() == null || req.prompt().isBlank() ? "" : "Question: " + req.prompt().strip();
        List<String> notes = req.prompt() == null ? List.of() : notices(req.prompt(), spec, data, ref, ctx.collegeName());
        ReportExporter.Report report = exporter.fromDashboard(ctx.collegeName(), spec, data, insight, notes, scope.length() > 140 ? scope.substring(0, 140) + "..." : scope);
        return pptx ? exporter.pptx(report) : exporter.pdf(report);
    }

    public byte[] exportBenchmark(CxoContext ctx, Benchmark b, boolean pptx) {
        ReportExporter.Report report = exporter.fromBenchmark(ctx.collegeName(), b);
        return pptx ? exporter.pptx(report) : exporter.pdf(report);
    }

    // ------------------------------------------------------------------ saved dashboards

    @Transactional(readOnly = true)
    public List<DashboardSummary> list(CxoContext ctx) {
        return dashboards.findAllByCollegeIdAndUserIdOrderByPinnedDescUpdatedAtDesc(ctx.collegeId(), ctx.userId()).stream()
                .map(d -> new DashboardSummary(d.getId(), d.getTitle(), d.getPrompt(), widgets(d), d.isPinned(), d.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public DashboardDetail get(CxoContext ctx, UUID id) {
        return detail(owned(ctx, id), ctx);
    }

    @Transactional
    public DashboardDetail save(CxoContext ctx, SaveRequest r) {
        if (dashboards.countByCollegeIdAndUserId(ctx.collegeId(), ctx.userId()) >= MAX_SAVED) {
            throw new DomainException("You can keep up to " + MAX_SAVED + " dashboards. Delete one first.", HttpStatus.BAD_REQUEST, "LIMIT");
        }
        DashboardSpec spec = cleanOrFail(ctx, r.spec());
        CxoDashboard d = dashboards.save(CxoDashboard.builder().collegeId(ctx.collegeId()).userId(ctx.userId()).title(r.title().strip())
                .prompt(r.prompt() == null ? null : r.prompt().strip()).specJson(write(spec)).pinned(Boolean.TRUE.equals(r.pinned())).build());
        return detail(d, ctx);
    }

    @Transactional
    public DashboardDetail update(CxoContext ctx, UUID id, SaveRequest r) {
        CxoDashboard d = owned(ctx, id);
        d.setTitle(r.title().strip());
        d.setPrompt(r.prompt() == null ? d.getPrompt() : r.prompt().strip());
        d.setSpecJson(write(cleanOrFail(ctx, r.spec())));
        if (r.pinned() != null) d.setPinned(r.pinned());
        return detail(dashboards.save(d), ctx);
    }

    @Transactional
    public void pin(CxoContext ctx, UUID id, boolean pinned) {
        CxoDashboard d = owned(ctx, id);
        d.setPinned(pinned);
        dashboards.save(d);
    }

    @Transactional
    public void delete(CxoContext ctx, UUID id) {
        dashboards.delete(owned(ctx, id));
    }

    // ------------------------------------------------------------------ helpers

    /** Branch codes and batch years of the signed-in college (used to check filters and shown in the prompt bar). */
    CollegeRef ref(CxoContext ctx) {
        String c = ctx.collegeId().toString();
        List<String> branches = jdbc.queryForList("select code from college_branches where college_id = ? order by code", String.class, c);
        Set<Integer> batches = new TreeSet<>(jdbc.queryForList("select grad_year from college_batches where college_id = ?", Integer.class, c));
        batches.addAll(jdbc.queryForList("select distinct batch_year from college_students where college_id = ? and deleted_at is null and batch_year is not null",
                Integer.class, c));
        return new CollegeRef(branches, new ArrayList<>(batches));
    }

    private DashboardSpec cleanOrFail(CxoContext ctx, DashboardSpec spec) {
        DashboardSpec clean = sanitizer.clean(spec, ref(ctx));
        if (clean.widgets().isEmpty()) throw new DomainException("This dashboard has no valid widgets", HttpStatus.BAD_REQUEST, "EMPTY_DASHBOARD");
        return clean;
    }

    private CxoDashboard owned(CxoContext ctx, UUID id) {
        return dashboards.findByIdAndCollegeIdAndUserId(id, ctx.collegeId(), ctx.userId())
                .orElseThrow(() -> new DomainException("Dashboard not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private DashboardDetail detail(CxoDashboard d, CxoContext ctx) {
        return new DashboardDetail(d.getId(), d.getTitle(), d.getPrompt(), sanitizer.clean(read(d.getSpecJson()), ref(ctx)), d.isPinned(), d.getUpdatedAt());
    }

    private int widgets(CxoDashboard d) {
        DashboardSpec s = read(d.getSpecJson());
        return s.widgets() == null ? 0 : s.widgets().size();
    }

    private DashboardSpec read(String s) {
        try {
            return json.readValue(s, DashboardSpec.class);
        } catch (Exception e) {
            return new DashboardSpec("Dashboard", null, List.of());
        }
    }

    private String write(DashboardSpec s) {
        try {
            return json.writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGone();
        }
    }

    private void pause() {
        if (paceMs == 0) return;
        try {
            Thread.sleep(paceMs); // lets each widget land on screen one after another
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(CxoContext ctx, String prompt, DashboardSpec spec, String source, String status, String error, long t0) {
        try {
            jdbc.update("""
                    insert into cxo_prompt_log (id, college_id, user_id, prompt, spec_json, source, status, error, duration_ms)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID().toString(), ctx.collegeId().toString(), ctx.userId().toString(), prompt.length() > 2000 ? prompt.substring(0, 2000) : prompt,
                    spec == null ? null : write(spec), source, status, error == null ? null : error.length() > 500 ? error.substring(0, 500) : error,
                    (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - t0));
        } catch (Exception e) {
            log.warn("Could not write the CXO prompt log: {}", e.toString());
        }
    }

    /** The browser closed the stream. */
    private static final class ClientGone extends RuntimeException {}
}
