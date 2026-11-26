package com.proveyu.student.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.proveyu.student.codeground.CodeGroundContent;
import com.proveyu.student.prep.application.CodeExecutionService;
import com.proveyu.student.prep.application.CodeRunnerUnavailableException;
import com.proveyu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Code ground: content, who may use it, and that only a run passing every test marks a problem solved. The judge is faked. */
@TestPropertySource(properties = {"code.execution.rate-limit-seconds=0", "code.ground.leaderboard-cache-seconds=0"})
class CodeGroundIT extends IntegrationTestBase {

    private static final String BASE = "/api/v1/college/student/code-ground";
    private static final String[] LANGUAGES = {"javascript", "python", "java", "cpp"};

    @MockBean private CodeExecutionService runner;
    @Autowired private CodeGroundContent content;

    @Test
    void contentIsComplete() {
        assertThat(content.topics()).hasSize(10);
        for (CodeGroundContent.Topic t : content.topics()) {
            assertThat(t.guide()).as(t.key()).hasSizeGreaterThanOrEqualTo(6);
            assertThat(t.guide().get(0).tag()).as(t.key()).isEqualTo("Why it matters");
            for (CodeGroundContent.Slide s : t.guide()) {
                assertThat(s.tag()).as(t.key()).isNotBlank();
                if (s.code() != null) assertThat(s.code()).as(t.key() + " " + s.title()).containsOnlyKeys(LANGUAGES);
            }
            assertThat(t.problems()).as(t.key()).hasSizeBetween(7, 8);
            for (CodeGroundContent.Problem p : t.problems()) {
                assertThat(p.tests()).as(p.key()).hasSizeGreaterThanOrEqualTo(4);
                assertThat(p.starters()).as(p.key()).containsOnlyKeys(LANGUAGES);
                assertThat(p.starters().get("javascript")).as(p.key()).contains("function solve(").contains("Write your code here");
                assertThat(p.starters().get("python")).as(p.key()).contains("def solve(").contains("Write your code here");
                assertThat(p.starters().get("java")).as(p.key()).contains("public class Main").contains(" solve(").contains("Write your code here");
                assertThat(p.starters().get("cpp")).as(p.key()).contains("int main()").contains(" solve(").contains("Write your code here");
                assertThat(p.hint()).as(p.key()).isNotBlank();
                assertThat(p.hiddenTests()).as(p.key()).hasSizeGreaterThanOrEqualTo(3);
                assertThat(p.hiddenTests()).as(p.key()).extracting(c -> c.input())
                        .doesNotContainAnyElementsOf(p.tests().stream().map(c -> c.input()).toList());
            }
        }
    }

    @Test
    void solvingAndReadingGuidesCountTowardsProgress() throws Exception {
        String tpo = registerCollege("cg");
        String branch = addBranch(tpo, "CSE");
        String roll = "C" + uid();
        String email = addStudent(tpo, branch, roll, 8.0, 0).get("email").asText();
        String otherEmail = addStudent(tpo, branch, "D" + uid(), 8.0, 0).get("email").asText();
        String student = activate(email);
        String other = activate(otherEmail);

        assertThat(get(BASE, tpo).status()).isEqualTo(403);                  // staff are not students
        assertThat(get(BASE, null).status()).isEqualTo(401);

        JsonNode overview = get(BASE, student).data();
        assertThat(overview.get("languages")).hasSize(4);
        assertThat(overview.get("languages").get(2).get("key").asText()).isEqualTo("java");
        JsonNode basics = overview.get("topics").get(0);
        assertThat(basics.get("done").asInt()).isZero();
        int total = basics.get("total").asInt();
        assertThat(total).isEqualTo(basics.get("problems").size() + 1);      // + the learning guide

        JsonNode problem = get(BASE + "/problems/sum-n", student).data();
        assertThat(problem.get("example").get("input").asText()).isEqualTo("5");
        assertThat(problem.has("tests")).isFalse();                          // only the example before running
        assertThat(problem.get("starters").get("python").asText()).contains("def solve(n):");
        assertThat(get(BASE + "/problems/nope", student).status()).isEqualTo(404);

        List<String> expected = content.problem("sum-n").orElseThrow().problem().tests().stream().map(c -> c.output()).toList();

        // one wrong output: not solved
        answer(expected.stream().map(s -> s.equals("0") ? "1" : s).toList());
        JsonNode wrong = post(BASE + "/problems/sum-n/submit", student, Map.of("code", "x")).data();
        assertThat(wrong.get("allPassed").asBoolean()).isFalse();
        assertThat(wrong.get("solved").asBoolean()).isFalse();
        assertThat(wrong.get("cases")).hasSize(expected.size());
        JsonNode sumN = get(BASE, student).data().get("topics").get(0).get("problems").get(0);
        assertThat(sumN.get("attempted").asBoolean()).isTrue();             // tried, not solved
        assertThat(sumN.get("solved").asBoolean()).isFalse();

        // a crash is not a pass even if something was printed
        when(runner.run(anyString(), anyString(), any())).thenReturn(new CodeExecutionService.Result(null,
                expected.stream().map(s -> new CodeExecutionService.Output(s, "boom", 1, false)).toList()));
        assertThat(post(BASE + "/problems/sum-n/submit", student, Map.of("code", "x")).data().get("allPassed").asBoolean()).isFalse();

        // a judge that is down records nothing and says so
        when(runner.run(anyString(), anyString(), any())).thenThrow(new CodeRunnerUnavailableException(CodeRunnerUnavailableException.Reason.UNREACHABLE));
        assertThat(post(BASE + "/problems/sum-n/submit", student, Map.of("code", "x")).status()).isEqualTo(503);

        // every test passes: solved, and it stays solved after a later failing run
        org.mockito.Mockito.reset(runner);
        answer(expected);
        assertThat(post(BASE + "/problems/sum-n/submit", student, Map.of("code", "x")).data().get("solved").asBoolean()).isTrue();
        answer(expected.stream().map(s -> "wrong").toList());
        JsonNode after = post(BASE + "/problems/sum-n/submit", student, Map.of("code", "x")).data();
        assertThat(after.get("allPassed").asBoolean()).isFalse();
        assertThat(after.get("solved").asBoolean()).isTrue();
        assertThat(post(BASE + "/problems/sum-n/submit", student, Map.of("code", "  ")).status()).isEqualTo(400);

        // the chosen language goes to the judge; an unknown one is refused before running
        answer(expected);
        post(BASE + "/problems/sum-n/submit", student, Map.of("language", "cpp", "code", "int solve(int n) { return 0; }"));
        org.mockito.Mockito.verify(runner, org.mockito.Mockito.times(2)).run(org.mockito.ArgumentMatchers.eq("cpp"), anyString(), any());
        assertThat(post(BASE + "/problems/sum-n/submit", student, Map.of("language", "ruby", "code", "x")).status()).isEqualTo(400);

        // the guide counts once, however often it is completed
        assertThat(get(BASE + "/topics/basics/guide", student).data().get("read").asBoolean()).isFalse();
        post(BASE + "/topics/basics/guide/complete", student, null);
        post(BASE + "/topics/basics/guide/complete", student, null);
        basics = get(BASE, student).data().get("topics").get(0);
        assertThat(basics.get("done").asInt()).isEqualTo(2);
        assertThat(basics.get("solved").asInt()).isEqualTo(1);
        assertThat(basics.get("guideRead").asBoolean()).isTrue();
        assertThat(basics.get("problems").get(0).get("attempted").asBoolean()).isFalse(); // solved is no longer "attempted"

        // another student's progress is separate
        assertThat(get(BASE, other).data().get("topics").get(0).get("done").asInt()).isZero();
    }

    @Test
    void hiddenTestsDecideAndNeverShowTheirData() throws Exception {
        String tpo = registerCollege("ch");
        String student = activate(addStudent(tpo, addBranch(tpo, "CSE"), "H" + uid(), 8.0, 0).get("email").asText());
        var sumN = content.problem("sum-n").orElseThrow().problem();
        List<String> visible = sumN.tests().stream().map(c -> c.output()).toList();
        assertThat(get(BASE + "/problems/sum-n", student).data().get("hiddenCount").asInt()).isEqualTo(sumN.hiddenTests().size());

        // wrong on a visible test: the hidden ones are not even run
        answer(visible.stream().map(s -> "wrong").toList());
        assertThat(post(BASE + "/problems/sum-n/submit", student, Map.of("code", "x")).data().get("hidden")).isEmpty();
        org.mockito.Mockito.verify(runner, org.mockito.Mockito.times(1)).run(anyString(), anyString(), any());

        // only the visible answers typed in: the hidden tests fail, nothing about them is sent back, and it is not solved
        org.mockito.Mockito.reset(runner);
        answer(visible, sumN.hiddenTests().stream().map(c -> "wrong").toList());
        JsonNode r = post(BASE + "/problems/sum-n/submit", student, Map.of("code", "x")).data();
        assertThat(r.get("allPassed").asBoolean()).isFalse();
        assertThat(r.get("solved").asBoolean()).isFalse();
        assertThat(r.get("hidden")).hasSize(sumN.hiddenTests().size());
        java.util.List<String> fields = new java.util.ArrayList<>();
        r.get("hidden").get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("index", "passed", "timedOut");
    }

    @Test
    void leaderboardRanksOnlyTheStudentsOwnCollege() throws Exception {
        String tpo = registerCollege("cl");
        String cse = addBranch(tpo, "CSE");
        String it = addBranch(tpo, "IT");
        String first = addStudent(tpo, cse, "L" + uid(), 8.0, 0).get("email").asText();
        String second = addStudent(tpo, it, "M" + uid(), 8.0, 0).get("email").asText();
        String a = activate(first);
        String b = activate(second);
        String otherTpo = registerCollege("co");
        String outsider = activate(addStudent(otherTpo, addBranch(otherTpo, "CSE"), "O" + uid(), 8.0, 0).get("email").asText());
        post(BASE + "/topics/basics/guide/complete", outsider, null);

        JsonNode empty = get(BASE + "/leaderboard", a).data();
        assertThat(empty.get("participants").asInt()).isZero();
        assertThat(empty.get("me").get("rank").isNull()).isTrue();

        answer(content.problem("sum-n").orElseThrow().problem().tests().stream().map(c -> c.output()).toList());
        post(BASE + "/problems/sum-n/submit", a, Map.of("code", "x"));      // Easy 10
        post(BASE + "/problems/sum-n/submit", a, Map.of("code", "x"));      // counted once
        post(BASE + "/topics/basics/guide/complete", a, null);           // 5
        post(BASE + "/topics/basics/guide/complete", b, null);           // 5

        jdbc.update("delete from code_ground_daily");                    // sum-n may be today's problem; the bonus has its own test
        JsonNode board = get(BASE + "/leaderboard?period=week", b).data();
        assertThat(board.get("participants").asInt()).isEqualTo(2);      // not the other college's student
        assertThat(board.get("top").get(0).get("points").asInt()).isEqualTo(15);
        assertThat(board.get("top").get(0).get("solved").asInt()).isEqualTo(1);
        assertThat(board.get("top").get(0).get("streak").asInt()).isEqualTo(1);
        assertThat(board.get("top").get(0).get("branch").asText()).isNotBlank();
        assertThat(board.get("top").get(0).has("rollNumber")).isFalse();
        assertThat(board.get("top").get(0).has("userId")).isFalse();
        assertThat(board.get("top").get(1).get("you").asBoolean()).isTrue();
        assertThat(board.get("me").get("rank").asInt()).isEqualTo(2);
        assertThat(board.get("me").get("pointsToNext").asInt()).isEqualTo(11);
        assertThat(get(BASE + "/leaderboard?scope=branch", b).data().get("participants").asInt()).isEqualTo(1);
        assertThat(get(BASE + "/leaderboard?period=year", b).status()).isEqualTo(400);

        // solved long ago: only on the all-time board, and the streak is gone
        jdbc.update("update code_ground_progress set done_at = utc_timestamp() - interval 20 day where user_id = "
                + "(select user_id from college_students where email = ?)", first);
        assertThat(get(BASE + "/leaderboard?period=week", a).data().get("participants").asInt()).isEqualTo(1);
        JsonNode all = get(BASE + "/leaderboard?period=all", a).data();
        assertThat(all.get("me").get("rank").asInt()).isEqualTo(1);
        assertThat(all.get("me").get("streak").asInt()).isZero();

        // staff see their own college with roll numbers; students cannot use the staff view
        String staffUrl = "/api/v1/college/code-ground/leaderboard?period=all";
        JsonNode staff = get(staffUrl, tpo).data();
        assertThat(staff.get("rows")).hasSize(2);
        assertThat(staff.get("rows").get(0).get("rollNumber").asText()).startsWith("L");
        assertThat(get(staffUrl + "&branchId=" + it, tpo).data().get("rows")).hasSize(1);
        assertThat(get(staffUrl, otherTpo).data().get("rows")).hasSize(1);
        assertThat(get(staffUrl, a).status()).isEqualTo(403);
    }

    @Test
    void runTriesOneInputAndKeepsPrintsApartWithoutSolving() throws Exception {
        String tpo = registerCollege("cr");
        String student = activate(addStudent(tpo, addBranch(tpo, "CSE"), "R" + uid(), 8.0, 0).get("email").asText());

        // what the student printed comes before the starter's marker; only what follows it is the answer
        judgePrints("debug 5\n\n##PROVEYU-ANSWER##\n15\n");
        JsonNode r = post(BASE + "/problems/sum-n/run", student, Map.of("code", "x")).data();
        assertThat(r.get("passed").asBoolean()).isTrue();
        assertThat(r.get("actual").asText()).isEqualTo("15\n");
        assertThat(r.get("printed").asText()).isEqualTo("debug 5\n");
        assertThat(get(BASE + "/problems/sum-n", student).data().get("solved").asBoolean()).isFalse(); // Run never solves

        // own input: no expected answer, so neither pass nor fail
        JsonNode own = post(BASE + "/problems/sum-n/run", student, Map.of("code", "x", "input", "12345")).data();
        assertThat(own.get("expected").isNull()).isTrue();
        assertThat(own.get("passed").isNull()).isTrue();
        org.mockito.Mockito.verify(runner).run(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(List.of("12345")));
        assertThat(post(BASE + "/problems/sum-n/run", student, Map.of("code", "x", "input", "9".repeat(70_000))).status()).isEqualTo(400);

        // printing the right answer yourself and then answering something else is wrong
        judgePrints("15\n\n##PROVEYU-ANSWER##\n0\n");
        assertThat(post(BASE + "/problems/sum-n/run", student, Map.of("code", "x")).data().get("passed").asBoolean()).isFalse();

        // a crash: what was printed is still shown
        when(runner.run(anyString(), anyString(), any())).thenReturn(new CodeExecutionService.Result(null,
                List.of(new CodeExecutionService.Output("reached here\n", "boom", 1, false))));
        JsonNode crash = post(BASE + "/problems/sum-n/run", student, Map.of("code", "x")).data();
        assertThat(crash.get("printed").asText()).isEqualTo("reached here\n");
        assertThat(crash.get("error").asText()).isEqualTo("boom");
    }

    @Test
    void codeIsSavedOnTheServerPerStudentAndLanguage() throws Exception {
        String tpo = registerCollege("cd");
        String branch = addBranch(tpo, "CSE");
        String mine = activate(addStudent(tpo, branch, "S" + uid(), 8.0, 0).get("email").asText());
        String other = activate(addStudent(tpo, branch, "T" + uid(), 8.0, 0).get("email").asText());
        String url = BASE + "/problems/sum-n/draft";

        assertThat(put(url, mine, Map.of("language", "python", "code", "first")).status()).isEqualTo(200);
        put(url, mine, Map.of("language", "python", "code", "second"));
        put(url, mine, Map.of("language", "java", "code", "class"));
        JsonNode drafts = get(BASE + "/problems/sum-n", mine).data().get("drafts");
        assertThat(drafts.get("python").asText()).isEqualTo("second");
        assertThat(drafts.get("java").asText()).isEqualTo("class");
        assertThat(get(BASE + "/problems/sum-n", other).data().get("drafts")).isEmpty();

        put(url, mine, Map.of("language", "python", "code", ""));            // Reset code
        assertThat(get(BASE + "/problems/sum-n", mine).data().get("drafts").has("python")).isFalse();
        assertThat(put(url, mine, Map.of("language", "ruby", "code", "x")).status()).isEqualTo(400);
        assertThat(put(url, mine, Map.of("language", "java", "code", "x".repeat(70_000))).status()).isEqualTo(400);
        assertThat(put(BASE + "/problems/nope/draft", mine, Map.of("language", "java", "code", "x")).status()).isEqualTo(404);
        assertThat(put(url, tpo, Map.of("language", "java", "code", "x")).status()).isEqualTo(403);
    }

    private void judgePrints(String stdout) {
        when(runner.run(anyString(), anyString(), any())).thenReturn(new CodeExecutionService.Result(null,
                List.of(new CodeExecutionService.Output(stdout, "", 0, false))));
    }

    @Test
    void theProblemOfTheDayGivesItsPointsAgainOnceADay() throws Exception {
        String tpo = registerCollege("cp");
        String student = activate(addStudent(tpo, addBranch(tpo, "CSE"), "P" + uid(), 8.0, 0).get("email").asText());
        JsonNode daily = get(BASE, student).data().get("daily");
        String key = daily.get("key").asText();
        int bonus = daily.get("bonus").asInt();
        assertThat(daily.get("solvedToday").asBoolean()).isFalse();
        assertThat(bonus).isPositive();

        judgeGetsEverythingRight(key);
        assertThat(post(BASE + "/problems/" + key + "/submit", student, Map.of("code", "x")).data().get("bonus").asInt()).isEqualTo(bonus);
        assertThat(post(BASE + "/problems/" + key + "/submit", student, Map.of("code", "x")).data().get("bonus").asInt()).isZero();
        assertThat(get(BASE, student).data().get("daily").get("solvedToday").asBoolean()).isTrue();
        assertThat(get(BASE + "/leaderboard", student).data().get("me").get("points").asInt()).isEqualTo(2 * bonus);
        assertThat(get(BASE + "/leaderboard", student).data().get("me").get("solved").asInt()).isEqualTo(1);

        JsonNode activity = get(BASE + "/activity", student).data();
        assertThat(activity.get("days")).hasSize(1);
        assertThat(activity.get("days").get(0).get("count").asInt()).isEqualTo(1);   // solved and daily: one problem
        assertThat(activity.get("currentStreak").asInt()).isEqualTo(1);
        assertThat(activity.get("to").asText()).isEqualTo(daily.get("date").asText());

        // another problem earns no bonus
        String other = key.equals("sum-n") ? "count-digits" : "sum-n";
        judgeGetsEverythingRight(other);
        assertThat(post(BASE + "/problems/" + other + "/submit", student, Map.of("code", "x")).data().get("bonus").asInt()).isZero();
    }

    @Test
    void everySubmissionIsKeptUpToTwentyPerProblem() throws Exception {
        String tpo = registerCollege("ch2");
        String branch = addBranch(tpo, "CSE");
        String mine = activate(addStudent(tpo, branch, "K" + uid(), 8.0, 0).get("email").asText());
        String other = activate(addStudent(tpo, branch, "J" + uid(), 8.0, 0).get("email").asText());
        String url = BASE + "/problems/sum-n/submissions";

        answer(content.problem("sum-n").orElseThrow().problem().tests().stream().map(c -> "wrong").toList());
        post(BASE + "/problems/sum-n/submit", mine, Map.of("language", "python", "code", "first try"));
        judgeGetsEverythingRight("sum-n");
        post(BASE + "/problems/sum-n/run", mine, Map.of("code", "only a run"));            // Run is not kept
        post(BASE + "/problems/sum-n/submit", mine, Map.of("language", "python", "code", "second try"));

        JsonNode list = get(url, mine).data();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(list.get(0).get("code").asText()).isEqualTo("second try");
        assertThat(list.get(0).get("passed").asInt()).isEqualTo(list.get(0).get("total").asInt());
        assertThat(list.get(0).get("createdAt").asText()).endsWith("Z");
        assertThat(list.get(1).get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(get(url, other).data()).isEmpty();

        for (int i = 0; i < 22; i++) post(BASE + "/problems/sum-n/submit", mine, Map.of("code", "again " + i));
        JsonNode kept = get(url, mine).data();
        assertThat(kept).hasSize(20);
        assertThat(kept.get(0).get("code").asText()).isEqualTo("again 21");
    }

    @Test
    void allSubmissionsCanBeFilteredAndOnlyTheOwnerReadsThem() throws Exception {
        String tpo = registerCollege("cm");
        String branch = addBranch(tpo, "CSE");
        String mine = activate(addStudent(tpo, branch, "V" + uid(), 8.0, 0).get("email").asText());
        String other = activate(addStudent(tpo, branch, "W" + uid(), 8.0, 0).get("email").asText());
        String url = BASE + "/submissions";

        judgeGetsEverythingRight("sum-n");
        post(BASE + "/problems/sum-n/submit", mine, Map.of("language", "python", "code", "my sum"));
        answer(java.util.Collections.nCopies(5, "nope"));   // count-digits has five visible tests
        post(BASE + "/problems/count-digits/submit", mine, Map.of("language", "java", "code", "my count"));

        JsonNode all = get(url, mine).data();
        assertThat(all.get("summary").get("total").asInt()).isEqualTo(2);
        assertThat(all.get("summary").get("accepted").asInt()).isEqualTo(1);
        assertThat(all.get("summary").get("solvedProblems").asInt()).isEqualTo(1);
        assertThat(all.get("items").get(0).get("problemKey").asText()).isEqualTo("count-digits");   // newest first
        assertThat(all.get("items").get(0).has("code")).isFalse();
        assertThat(all.get("hasMore").asBoolean()).isFalse();

        assertThat(get(url + "?verdict=ACCEPTED", mine).data().get("items")).hasSize(1);
        assertThat(get(url + "?language=java", mine).data().get("items")).hasSize(1);
        assertThat(get(url + "?q=sum of", mine).data().get("items")).hasSize(1);
        assertThat(get(url + "?difficulty=HARD", mine).data().get("items")).isEmpty();
        assertThat(get(url + "?stuck=true", mine).data().get("items").get(0).get("problemKey").asText()).isEqualTo("count-digits");
        assertThat(get(url + "?days=1", mine).data().get("items")).hasSize(2);

        jdbc.update("update code_ground_submissions set created_at = created_at - interval 10 day where id = ?", all.get("items").get(1).get("id").asLong());
        assertThat(get(url + "?days=7", mine).data().get("items")).hasSize(1);
        assertThat(get(url + "?days=30", mine).data().get("items")).hasSize(2);
        String longAgo = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(5).toString();
        assertThat(get(url + "?to=" + longAgo, mine).data().get("items")).hasSize(1);

        long id = all.get("items").get(0).get("id").asLong();
        assertThat(get(url + "/" + id, mine).data().get("code").asText()).isEqualTo("my count");
        assertThat(get(url + "/" + id, other).status()).isEqualTo(404);
        assertThat(get(url, other).data().get("items")).isEmpty();
    }

    private void judgeGetsEverythingRight(String key) {
        var p = content.problem(key).orElseThrow().problem();
        Map<String, String> answers = new java.util.HashMap<>();
        p.tests().forEach(c -> answers.put(c.input(), c.output()));
        p.hiddenTests().forEach(c -> answers.put(c.input(), c.output()));
        // doAnswer, and null-safe: Mockito calls an earlier answer with empty arguments while a new one is being set up
        org.mockito.Mockito.doAnswer(call -> new CodeExecutionService.Result(null,
                java.util.Optional.ofNullable(call.<List<String>>getArgument(2)).orElse(List.of()).stream()
                        .map(in -> new CodeExecutionService.Output(answers.get(in) + "\n", "", 0, false)).toList()))
                .when(runner).run(anyString(), anyString(), any());
    }

    /** The judge prints these for the visible tests, and the right answers for the hidden ones. */
    private void answer(List<String> outputs) {
        answer(outputs, content.problem("sum-n").orElseThrow().problem().hiddenTests().stream().map(c -> c.output()).toList());
    }

    private void answer(List<String> visible, List<String> hidden) {
        List<String> hiddenInputs = content.problem("sum-n").orElseThrow().problem().hiddenTests().stream().map(c -> c.input()).toList();
        when(runner.run(anyString(), anyString(), any())).thenAnswer(call -> new CodeExecutionService.Result(null,
                (hiddenInputs.equals(call.getArgument(2)) ? hidden : visible).stream()
                        .map(s -> new CodeExecutionService.Output(s + "\n", "", 0, false)).toList()));
    }

    private String activate(String email) throws Exception {
        String roll = jdbcRoll(email);
        var start = post("/api/v1/college/auth/student-signup", null, Map.of("email", email, "rollNumber", roll));
        post("/api/v1/auth/reset-password", null, Map.of("email", email, "resetToken", start.data().get("devOtp").asText(), "newPassword", "Student123"));
        return login(email, "Student123");
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String jdbcRoll(String email) {
        return jdbc.queryForObject("select roll_number from college_students where email = ?", String.class, email);
    }
}
