package com.proveyu.student.codeground;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.student.prep.domain.PrepModel.IoCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * The Code ground topics, learning guides and problems students see: those shipped in {@code resources/codeground/*.json} (generated
 * and checked by {@code scripts/codeground/build.mjs}) plus what a platform admin has published, minus what the admin has hidden.
 * The merged view is cached briefly; an admin change on this server shows at once, on another server within the cache time.
 */
@Slf4j
@Component
public class CodeGroundContent {

    /** tag: Why it matters, Real-world use, Core idea, Syntax, Pattern, ... code: one snippet per language key, or null. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slide(String tag, String title, List<String> points, Map<String, String> code) {}

    /** One parameter of solve(). type: int, str, ints, words, rows or tree. */
    public record Arg(String name, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Problem(String key, String title, String difficulty, String statement, List<String> inputFormat, String hint,
                          String note, List<Arg> args, String returnType, Map<String, String> starters, List<IoCase> tests,
                          List<IoCase> hiddenTests) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Topic(String key, String title, String summary, List<Slide> guide, List<Problem> problems) {}

    /** A problem with the topic it belongs to. */
    public record Located(Topic topic, Problem problem) {}

    private record Snapshot(List<Topic> topics, Map<String, Topic> byTopic, Map<String, Located> byProblem, Map<String, String> difficulties,
                            long loadedAt) {}

    private static final long CACHE_NANOS = 15_000_000_000L;
    private static final List<String> LEVELS = List.of("EASY", "MEDIUM", "HARD");

    private final ObjectMapper json;
    private final CodeGroundAuthored authored;
    private final List<Topic> builtIn;
    private final Set<String> builtInProblems = new HashSet<>();
    private volatile Snapshot snapshot;

    public CodeGroundContent(ObjectMapper json, CodeGroundAuthored authored) {
        this.json = json;
        this.authored = authored;
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:codeground/*.json");
            Arrays.sort(files, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            List<Topic> all = new ArrayList<>();
            for (Resource f : files) {
                try (InputStream in = f.getInputStream()) {
                    all.add(json.readValue(in, Topic.class));
                }
            }
            this.builtIn = List.copyOf(all);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the Code ground content", e);
        }
        Set<String> topicKeys = new HashSet<>();
        for (Topic t : builtIn) {
            if (!topicKeys.add(t.key())) throw new IllegalStateException("Duplicate Code ground topic " + t.key());
            for (Problem p : t.problems()) {
                if (!builtInProblems.add(p.key())) throw new IllegalStateException("Duplicate Code ground problem " + p.key());
            }
        }
    }

    /** Topics in order, each with its problems from easy to hard. */
    public List<Topic> topics() {
        return current().topics();
    }

    public Optional<Topic> topic(String key) {
        return Optional.ofNullable(current().byTopic().get(key));
    }

    public Optional<Located> problem(String key) {
        return Optional.ofNullable(current().byProblem().get(key));
    }

    /** The difficulty of any problem ever offered, also one taken out of use since, so points already earned stay. */
    public Optional<String> difficultyOf(String key) {
        return Optional.ofNullable(current().difficulties().get(key));
    }

    /** The shipped topics, untouched by the admin. */
    public List<Topic> builtInTopics() {
        return builtIn;
    }

    public boolean isBuiltInProblem(String key) {
        return builtInProblems.contains(key);
    }

    public boolean isBuiltInTopic(String key) {
        return builtIn.stream().anyMatch(t -> t.key().equals(key));
    }

    /** Call after an admin change. */
    public void refresh() {
        snapshot = null;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null || System.nanoTime() - s.loadedAt() > CACHE_NANOS) {
            s = load();
            snapshot = s;
        }
        return s;
    }

    private Snapshot load() {
        Set<String> hidden = authored.hidden();
        Map<String, List<Problem>> added = new HashMap<>();
        Map<String, String> difficulties = new HashMap<>();
        builtIn.forEach(t -> t.problems().forEach(p -> difficulties.put(p.key(), p.difficulty())));
        for (CodeGroundAuthored.ProblemRow row : authored.problems()) {
            // an unpublished problem still counts for the points of students who solved it while it was published
            Spec spec = read(row.publishedSpec() != null ? row.publishedSpec() : row.spec(), Spec.class);
            if (spec == null || builtInProblems.contains(row.key())) continue;
            difficulties.put(row.key(), spec.difficulty());
            if (row.publishedSpec() != null) added.computeIfAbsent(spec.topicKey(), k -> new ArrayList<>()).add(spec.toProblem());
        }

        List<Topic> all = new ArrayList<>(builtIn);
        for (CodeGroundAuthored.TopicRow row : authored.topics()) {
            if (isBuiltInTopic(row.key())) continue;
            List<Slide> guide = read(row.guide(), new TypeReference<List<Slide>>() {});
            all.add(new Topic(row.key(), row.title(), row.summary(), guide == null ? List.of() : guide, List.of()));
        }

        List<Topic> topics = new ArrayList<>();
        Map<String, Topic> byTopic = new HashMap<>();
        Map<String, Located> byProblem = new HashMap<>();
        for (Topic t : all) {
            List<Problem> problems = new ArrayList<>(t.problems().stream().filter(p -> !hidden.contains(p.key())).toList());
            problems.addAll(added.getOrDefault(t.key(), List.of()));
            problems.sort(Comparator.comparingInt(p -> LEVELS.indexOf(p.difficulty())));   // stable: keeps the authored order within a level
            if (problems.isEmpty() && !builtIn.contains(t)) continue;                     // a new topic appears with its first problem
            Topic merged = new Topic(t.key(), t.title(), t.summary(), t.guide(), List.copyOf(problems));
            topics.add(merged);
            byTopic.put(merged.key(), merged);
            problems.forEach(p -> byProblem.put(p.key(), new Located(merged, p)));
        }
        return new Snapshot(List.copyOf(topics), byTopic, byProblem, difficulties, System.nanoTime());
    }

    private <T> T read(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            log.error("[CODE_GROUND] stored content could not be read: {}", e.getOriginalMessage());
            return null; // one bad row must not take Code ground down
        }
    }

    private <T> T read(String text, TypeReference<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            log.error("[CODE_GROUND] stored content could not be read: {}", e.getOriginalMessage());
            return null;
        }
    }

    /** A problem as an admin writes it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Spec(String key, String topicKey, String title, String difficulty, String statement, List<Arg> args, String returnType,
                       String hint, String note, List<IoCase> tests, List<IoCase> hiddenTests, Solution solution) {

        Problem toProblem() {
            return new Problem(key, title, difficulty, statement, CodeGroundStarters.inputFormat(args), hint,
                    note == null || note.isBlank() ? null : note, args, returnType, CodeGroundStarters.starters(args, returnType),
                    tests, hiddenTests);
        }
    }

    /** The admin's reference solution: solve() (and any helpers) in one language. Never sent to students. */
    public record Solution(String language, String code) {}
}
