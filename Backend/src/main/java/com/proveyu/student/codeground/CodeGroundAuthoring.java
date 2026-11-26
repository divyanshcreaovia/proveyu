package com.proveyu.student.codeground;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.admin.application.AdminAuditService;
import com.proveyu.shared.error.DomainException;
import com.proveyu.student.codeground.CodeGroundContent.*;
import com.proveyu.student.prep.application.CodeExecutionService;
import com.proveyu.student.prep.domain.PrepModel.IoCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Platform admin: new topics and problems (one at a time or from a JSON file), on top of the built-in ones. A saved problem is a draft;
 * publishing runs the reference solution on the judge against every test and refuses on any mismatch, so students never get a problem
 * whose expected answers are wrong or whose starter does not compile.
 */
@Service
@RequiredArgsConstructor
public class CodeGroundAuthoring {

    private static final int MAX_UPLOAD_PROBLEMS = 100;
    private static final int MAX_SHOWN = 600;

    public enum Status { PUBLISHED, DRAFT, CHANGED, HIDDEN }

    public record ProblemSummary(String key, String title, String difficulty, boolean builtIn, Status status) {}

    public record TopicSummary(String key, String title, String summary, boolean builtIn, List<Slide> guide, List<ProblemSummary> problems) {}

    /** The saved version of an admin problem. */
    public record Authored(Spec spec, Status status) {}

    public record TopicInput(String key, String title, String summary, List<Slide> guide) {}

    public record UploadTopic(String key, String title, String summary, List<Slide> guide, List<Spec> problems) {}

    public record Upload(List<UploadTopic> topics) {}

    public record Uploaded(int topics, List<String> problems) {}

    public record Failure(String test, String input, String expected, String got, String error) {}

    /** ok false: nothing was published, see errors / compileError / failures. */
    public record Published(boolean ok, List<String> errors, String compileError, List<Failure> failures) {}

    public record Template(String language, String starter, String solve) {}

    private final CodeGroundContent content;
    private final CodeGroundAuthored store;
    private final CodeExecutionService runner;
    private final ObjectMapper json;
    private final AdminAuditService audit;

    // ---------------------------------------------------------------- reading

    public List<TopicSummary> catalogue() {
        Set<String> hidden = store.hidden();
        List<CodeGroundAuthored.ProblemRow> rows = store.problems();
        List<TopicSummary> out = new ArrayList<>();
        for (Topic t : content.builtInTopics()) {
            List<ProblemSummary> problems = new ArrayList<>(t.problems().stream().map(p -> new ProblemSummary(p.key(), p.title(), p.difficulty(),
                    true, hidden.contains(p.key()) ? Status.HIDDEN : Status.PUBLISHED)).toList());
            problems.addAll(summaries(rows, t.key()));
            out.add(new TopicSummary(t.key(), t.title(), t.summary(), true, t.guide(), problems));
        }
        for (CodeGroundAuthored.TopicRow row : store.topics()) {
            if (content.isBuiltInTopic(row.key())) continue;
            out.add(new TopicSummary(row.key(), row.title(), row.summary(), false, guideOf(row.guide()), summaries(rows, row.key())));
        }
        return out;
    }

    private List<ProblemSummary> summaries(List<CodeGroundAuthored.ProblemRow> rows, String topicKey) {
        return rows.stream().filter(r -> r.topicKey().equals(topicKey)).map(r -> {
            Spec s = spec(r.spec());
            return new ProblemSummary(r.key(), s.title(), s.difficulty(), false, status(r));
        }).toList();
    }

    public Authored authored(String key) {
        CodeGroundAuthored.ProblemRow row = row(key);
        return new Authored(spec(row.spec()), status(row));
    }

    /** Empty starters and solve() outlines for a signature, in every language. */
    public List<Template> templates(List<Arg> args, String returnType) {
        Spec probe = new Spec("probe-key", "probe-topic", "Probe", "EASY", "Probe statement", args, returnType, "Probe hint", null,
                List.of(), List.of(), null);
        List<String> errors = CodeGroundValidator.problem(probe).stream()
                .filter(e -> e.startsWith("Parameter") || e.startsWith("solve()") || e.startsWith("Return")).toList();
        if (!errors.isEmpty()) throw invalid(errors);
        return CodeGroundStarters.starters(args, returnType).entrySet().stream()
                .map(e -> new Template(e.getKey(), e.getValue(), CodeGroundStarters.solveTemplate(e.getKey(), e.getValue()))).toList();
    }

    // ---------------------------------------------------------------- writing

    public void saveTopic(UUID admin, TopicInput t) {
        if (content.isBuiltInTopic(t.key())) throw bad("This is a built-in topic; add problems to it, or create a topic with another key");
        List<String> errors = CodeGroundValidator.topic(t.key(), t.title(), t.summary(), t.guide());
        if (!errors.isEmpty()) throw invalid(errors);
        store.saveTopic(t.key(), t.title().strip(), t.summary().strip(), write(t.guide() == null ? List.of() : t.guide()));
        content.refresh();
        audit.log(admin, "CODE_GROUND_TOPIC_SAVE", "CODE_GROUND_TOPIC", t.key(), "Saved topic " + t.title());
    }

    public void deleteTopic(UUID admin, String key) {
        if (content.isBuiltInTopic(key)) throw bad("Built-in topics cannot be deleted");
        if (store.problems().stream().anyMatch(p -> p.topicKey().equals(key))) throw bad("Delete or move this topic's problems first");
        store.deleteTopic(key);
        content.refresh();
        audit.log(admin, "CODE_GROUND_TOPIC_DELETE", "CODE_GROUND_TOPIC", key, "Deleted topic " + key);
    }

    /** Saves the draft; what students see changes only on publish. */
    public void saveProblem(UUID admin, Spec spec) {
        List<String> errors = check(spec, topicKeys());
        if (!errors.isEmpty()) throw invalid(errors);
        store.saveProblem(spec.key(), spec.topicKey(), write(spec));
        audit.log(admin, "CODE_GROUND_PROBLEM_SAVE", "CODE_GROUND_PROBLEM", spec.key(), "Saved " + spec.title());
    }

    /** A whole file, all or nothing: every problem is saved as a draft. */
    @Transactional
    public Uploaded upload(UUID admin, Upload upload) {
        if (upload == null || upload.topics() == null || upload.topics().isEmpty()) throw bad("The file has no \"topics\" list");
        List<String> errors = new ArrayList<>();
        Set<String> topics = topicKeys();
        Set<String> seen = new HashSet<>();
        List<Spec> specs = new ArrayList<>();
        List<UploadTopic> newTopics = new ArrayList<>();
        for (UploadTopic t : upload.topics()) {
            if (t == null || t.key() == null) {
                errors.add("Every topic needs a key");
                continue;
            }
            boolean known = topics.contains(t.key());
            // an existing topic may be named only by its key; a title (for a topic of your own) updates it
            if (!content.isBuiltInTopic(t.key()) && (!known || t.title() != null)) {
                CodeGroundValidator.topic(t.key(), t.title(), t.summary(), t.guide()).forEach(e -> errors.add("Topic " + t.key() + ": " + e));
                newTopics.add(t);
            }
            topics.add(t.key());
            for (Spec p : t.problems() == null ? List.<Spec>of() : t.problems()) {
                if (p == null) continue;
                Spec spec = new Spec(p.key(), t.key(), p.title(), p.difficulty(), p.statement(), p.args(), p.returnType(), p.hint(), p.note(),
                        p.tests(), p.hiddenTests(), p.solution());
                check(spec, topics).forEach(e -> errors.add((spec.key() == null ? "A problem" : spec.key()) + ": " + e));
                if (spec.key() != null && !seen.add(spec.key())) errors.add(spec.key() + ": appears twice in the file");
                specs.add(spec);
            }
        }
        if (specs.size() > MAX_UPLOAD_PROBLEMS) errors.add("At most " + MAX_UPLOAD_PROBLEMS + " problems in one file");
        if (!errors.isEmpty()) throw invalid(errors);
        newTopics.forEach(t -> store.saveTopic(t.key(), t.title().strip(), t.summary().strip(), write(t.guide() == null ? List.of() : t.guide())));
        specs.forEach(s -> store.saveProblem(s.key(), s.topicKey(), write(s)));
        content.refresh();
        audit.log(admin, "CODE_GROUND_UPLOAD", "CODE_GROUND", "upload", "Uploaded " + newTopics.size() + " topic(s), " + specs.size() + " problem(s)");
        return new Uploaded(newTopics.size(), specs.stream().map(Spec::key).toList());
    }

    /** Checks the saved version on the judge and, only if everything matches, shows it to students. */
    public Published publish(UUID admin, String key) {
        CodeGroundAuthored.ProblemRow row = row(key);
        Spec s = spec(row.spec());
        List<String> errors = check(s, topicKeys());
        if (!errors.isEmpty()) return new Published(false, errors, null, List.of());

        Map<String, String> starters = CodeGroundStarters.starters(s.args(), s.returnType());
        String language = s.solution().language();
        String program = CodeGroundStarters.withSolution(language, starters.get(language), s.solution().code());

        List<Failure> failures = new ArrayList<>();
        for (boolean hidden : new boolean[] {false, true}) {
            List<IoCase> tests = hidden ? s.hiddenTests() : s.tests();
            CodeExecutionService.Result r = runner.run(language, program, tests.stream().map(IoCase::input).toList());
            if (r.compileError() != null) return new Published(false, List.of("The reference solution does not compile"), r.compileError(), List.of());
            for (int i = 0; i < tests.size(); i++) {
                CodeExecutionService.Output o = r.outputs().get(i);
                if (CodeGroundOutput.passed(o, tests.get(i).output())) continue;
                String error = o.timedOut() ? "Time limit exceeded" : o.exitCode() != 0 ? cut(o.stderr()) : null;
                failures.add(new Failure((hidden ? "Hidden test " : "Test ") + (i + 1), cut(tests.get(i).input()), cut(tests.get(i).output()),
                        cut(CodeGroundOutput.split(o).actual()), error));
            }
        }
        if (!failures.isEmpty()) return new Published(false, List.of("The reference solution does not give the expected output"), null, failures);

        // the empty starters of the other languages must build and run too (a parameter name or type could break one)
        for (Map.Entry<String, String> e : starters.entrySet()) {
            if (e.getKey().equals(language)) continue;
            CodeExecutionService.Result r = runner.run(e.getKey(), e.getValue(), List.of(s.tests().get(0).input()));
            if (r.compileError() != null || r.outputs().get(0).exitCode() != 0) {
                String why = r.compileError() != null ? r.compileError() : cut(r.outputs().get(0).stderr());
                return new Published(false, List.of("The " + e.getKey() + " starter does not run; check the parameter names and types"), why, List.of());
            }
        }
        store.publish(key, row.spec());
        content.refresh();
        audit.log(admin, "CODE_GROUND_PUBLISH", "CODE_GROUND_PROBLEM", key, "Published " + s.title());
        return new Published(true, List.of(), null, List.of());
    }

    public void unpublish(UUID admin, String key) {
        row(key);
        store.publish(key, null);
        content.refresh();
        audit.log(admin, "CODE_GROUND_UNPUBLISH", "CODE_GROUND_PROBLEM", key, "Unpublished " + key);
    }

    public void delete(UUID admin, String key) {
        row(key);
        if (store.used(key)) throw bad("Students have already worked on this problem. Unpublish it instead, so their history and points stay");
        store.deleteProblem(key);
        content.refresh();
        audit.log(admin, "CODE_GROUND_PROBLEM_DELETE", "CODE_GROUND_PROBLEM", key, "Deleted " + key);
    }

    public void setHidden(UUID admin, String key, boolean hidden) {
        if (!content.isBuiltInProblem(key)) throw bad("Only built-in problems are hidden; unpublish your own");
        store.setHidden(key, hidden);
        content.refresh();
        audit.log(admin, hidden ? "CODE_GROUND_HIDE" : "CODE_GROUND_SHOW", "CODE_GROUND_PROBLEM", key, (hidden ? "Hid " : "Showed ") + key);
    }

    // ---------------------------------------------------------------- helpers

    private List<String> check(Spec spec, Set<String> topics) {
        List<String> errors = new ArrayList<>(CodeGroundValidator.problem(spec));
        if (spec != null && spec.key() != null && content.isBuiltInProblem(spec.key())) {
            errors.add("A built-in problem has this key; choose another");
        }
        if (spec != null && spec.topicKey() != null && !topics.contains(spec.topicKey())) errors.add("Unknown topic '" + spec.topicKey() + "'");
        return errors;
    }

    private Set<String> topicKeys() {
        Set<String> keys = new HashSet<>();
        content.builtInTopics().forEach(t -> keys.add(t.key()));
        store.topics().forEach(t -> keys.add(t.key()));
        return keys;
    }

    private static Status status(CodeGroundAuthored.ProblemRow r) {
        if (r.publishedSpec() == null) return Status.DRAFT;
        return r.publishedSpec().equals(r.spec()) ? Status.PUBLISHED : Status.CHANGED;
    }

    private CodeGroundAuthored.ProblemRow row(String key) {
        return store.problem(key).orElseThrow(() -> new DomainException("Problem not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private Spec spec(String text) {
        try {
            return json.readValue(text, Spec.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored Code ground problem is unreadable", e);
        }
    }

    private List<Slide> guideOf(String text) {
        try {
            return json.readValue(text, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cut(String s) {
        return s == null || s.length() <= MAX_SHOWN ? s : s.substring(0, MAX_SHOWN) + " …";
    }

    private static DomainException bad(String message) {
        return new DomainException(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    private static DomainException invalid(List<String> errors) {
        String shown = String.join(" · ", errors.subList(0, Math.min(8, errors.size())));
        return bad(errors.size() > 8 ? shown + " · and " + (errors.size() - 8) + " more" : shown);
    }
}
