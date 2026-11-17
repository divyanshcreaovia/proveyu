package com.proveyu.interview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.interview.domain.AnswerEvaluation;
import com.proveyu.interview.domain.InterviewModel.InterviewType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts of the AI interview that do not need a model: plan repair, score clamping, transcript cleaning, schemas. */
class InterviewPromptsTest {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode node(String s) throws Exception {
        return json.readTree(s);
    }

    @Test
    void planAlwaysStartsWithIntroductionAndIsToppedUpToTheRequestedCount() throws Exception {
        JsonNode reply = node("{\"questions\":[{\"question\":\"Tell me about yourself\",\"topic\":\"x\"},"
                + "{\"question\":\"1. How does a hash map work internally\",\"topic\":\"DSA\"},"
                + "{\"question\":\"How does a hash map work internally\",\"topic\":\"dup\"},{\"question\":\"short\"}]}");
        List<InterviewPrompts.PlannedQuestion> plan = InterviewPrompts.readPlan(reply, InterviewType.TECHNICAL, 5, List.of());
        assertThat(plan).hasSize(5);
        assertThat(plan.get(0).text()).containsIgnoringCase("introduce yourself");
        assertThat(plan.get(1).text()).isEqualTo("How does a hash map work internally?"); // numbering stripped, '?' added
        assertThat(plan.stream().map(InterviewPrompts.PlannedQuestion::text).distinct()).hasSize(5);
    }

    @Test
    void planWithoutAnyModelReplyUsesTheFixedBank() {
        for (InterviewType t : InterviewType.values()) {
            assertThat(InterviewPrompts.readPlan(null, t, 6, List.of())).hasSize(6);
            assertThat(InterviewPrompts.readPlan(null, t, 6, List.of("Java", "SQL"))).hasSize(6);
        }
        assertThat(InterviewPrompts.readPlan(null, InterviewType.MIXED, 6, List.of()).get(5).topic()).isEqualTo("Motivation"); // HR question last
    }

    @Test
    void scoresAreClampedToZeroToFive() throws Exception {
        InterviewPrompts.Score s = InterviewPrompts.readScore(node("{\"content\":9,\"relevance\":\"2\",\"communication\":3.6,\"confidence\":-4,\"feedback\":\" ok \"}"));
        assertThat(List.of(s.content(), s.relevance(), s.communication(), s.confidence())).containsExactly(5, 2, 4, 0);
        assertThat(s.feedback()).isEqualTo("ok");
        assertThat(s.betterAnswer()).isNull();
    }

    @Test
    void noRealAnswerIsZeroInEveryScoreEvenIfFluent() throws Exception {
        // "I don't know" said clearly: the model gives content 0 but communication 4; the whole answer must still be 0.
        InterviewPrompts.Score s = InterviewPrompts.readScore(node("{\"content\":0,\"relevance\":3,\"communication\":4,\"confidence\":4,\"feedback\":\"f\",\"betterAnswer\":\"b\"}"));
        assertThat(List.of(s.content(), s.relevance(), s.communication(), s.confidence())).containsOnly(0);
        assertThat(s.betterAnswer()).isEqualTo("b");
        InterviewPrompts.Score off = InterviewPrompts.readScore(node("{\"content\":2,\"relevance\":0,\"communication\":5,\"confidence\":5}"));
        assertThat(List.of(off.content(), off.relevance(), off.communication(), off.confidence())).containsOnly(0);
        InterviewPrompts.Score missing = InterviewPrompts.readScore(node("{}"));
        assertThat(missing.content()).isZero();
    }

    @Test
    void skippedScoresZeroAndKeepsTheModelAnswer() {
        InterviewPrompts.Score s = InterviewPrompts.skippedScore("Key points");
        assertThat(List.of(s.content(), s.relevance(), s.communication(), s.confidence())).containsOnly(0);
        assertThat(s.feedback()).contains("skipped").contains("0");
        assertThat(s.betterAnswer()).isEqualTo("Key points");
    }

    @Test
    void followUpRejectsEmptyAndRepeatedQuestions() throws Exception {
        List<String> none = List.of();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"\"}"), "Q", none)).isNull();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"None needed\"}"), "Q", none)).isNull();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"What is a list\"}"), "What is a list", none)).isNull();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"Which database did you use there\"}"), "Q", none)).isEqualTo("Which database did you use there?");
    }

    @Test
    void followUpAboutAnotherTechnologyIsDropped() throws Exception {
        List<String> java = List.of("Java", "Spring Boot");
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"How would you do the same thing in Python\"}"), "Q", java)).isNull();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"How did Spring Boot help you in that project\"}"), "Q", java))
                .isEqualTo("How did Spring Boot help you in that project?");
    }

    @Test
    void planDropsQuestionsOutsideTheStackAndTopsUpFromTheStackBank() throws Exception {
        JsonNode reply = node("{\"questions\":[{\"question\":\"What is the difference between a list and a tuple in Python?\",\"topic\":\"Python\"},"
                + "{\"question\":\"How does garbage collection work in the JVM?\",\"topic\":\"Java\"},"
                + "{\"question\":\"Explain closures in JavaScript with an example\",\"topic\":\"JS\"},"
                + "{\"question\":\"How would you build a data pipeline in Apache Spark?\",\"topic\":\"Spark\"},"
                + "{\"question\":\"How do you react when a teammate misses a deadline?\",\"topic\":\"Teamwork\"}]}");
        List<String> java = List.of("Java");
        List<InterviewPrompts.PlannedQuestion> plan = InterviewPrompts.readPlan(reply, InterviewType.TECHNICAL, 6, java);
        assertThat(plan).hasSize(6);
        assertThat(plan).extracting(InterviewPrompts.PlannedQuestion::text).noneMatch(t -> t.contains("Python") || t.contains("JavaScript") || t.contains("Spark"));
        assertThat(plan).extracting(InterviewPrompts.PlannedQuestion::text).contains("How does garbage collection work in the JVM?",
                "How do you react when a teammate misses a deadline?"); // "react" the verb is not React
        assertThat(plan).extracting(InterviewPrompts.PlannedQuestion::text).anyMatch(t -> t.contains("in Java")); // topped up with Java questions
        for (var q : plan) assertThat(TechStack.offStack(q.text(), java)).isNull();
    }

    @Test
    void fixedBankStaysOnTheStack() {
        for (List<String> stack : List.of(List.of("Python", "Django"), List.of("JavaScript", "React"), List.of("C++"), List.of("Java", "MySQL"))) {
            for (InterviewType t : InterviewType.values()) {
                for (var q : InterviewPrompts.fallback(t, stack)) assertThat(TechStack.offStack(q.text(), stack)).as(q.text()).isNull();
            }
        }
        assertThat(InterviewPrompts.fallback(InterviewType.TECHNICAL, List.of("Python"))).extracting(InterviewPrompts.PlannedQuestion::text)
                .anyMatch(t -> t.contains("Python")).noneMatch(t -> t.contains("Java"));
    }

    @Test
    void whisperNoiseMarkersAreNotCountedAsWords() {
        assertThat(AiGateway.cleanTranscript(" [BLANK_AUDIO] ")).isEmpty();
        assertThat(AiGateway.cleanTranscript("(music) Hello  there [laughs]")).isEqualTo("Hello there");
        assertThat(InterviewPrompts.words("")).isZero();
        assertThat(InterviewPrompts.fillerCount("Um I basically, uh, like it")).isEqualTo(4);
    }

    @Test
    void schemasAreValidJson() throws Exception {
        for (String s : List.of(InterviewPrompts.planSchema(4), InterviewPrompts.FOLLOW_UP_SCHEMA, InterviewPrompts.SCORE_SCHEMA, InterviewPrompts.SUMMARY_SCHEMA,
                InterviewPrompts.MODEL_ANSWER_SCHEMA)) {
            assertThat(node(s).path("type").asText()).isEqualTo("object");
        }
        assertThat(node(InterviewPrompts.planSchema(4)).at("/properties/questions/minItems").asInt()).isEqualTo(4);
        assertThat(node(InterviewPrompts.SCORE_SCHEMA).at("/properties/content/enum/0").asInt()).isZero(); // 0 is a valid score
    }

    @Test
    void answerPercentMapsTheRubricToZeroToHundred() {
        AnswerEvaluation all0 = AnswerEvaluation.builder().content(0).relevance(0).communication(0).confidence(0).build();
        AnswerEvaluation all5 = AnswerEvaluation.builder().content(5).relevance(5).communication(5).confidence(5).build();
        AnswerEvaluation all1 = AnswerEvaluation.builder().content(1).relevance(1).communication(1).confidence(1).build();
        AnswerEvaluation mixed = AnswerEvaluation.builder().content(4).relevance(5).communication(3).confidence(3).build();
        AnswerEvaluation fluentNothing = AnswerEvaluation.builder().content(0).relevance(4).communication(5).confidence(5).build();
        assertThat(InterviewEvaluator.answerPercent(all0)).isZero();               // skipped
        assertThat(InterviewEvaluator.answerPercent(fluentNothing)).isZero();      // no real answer, however fluent
        assertThat(InterviewEvaluator.answerPercent(all1)).isEqualTo(20.0);        // almost nothing correct, but an attempt
        assertThat(InterviewEvaluator.answerPercent(all5)).isEqualTo(100.0);
        assertThat(InterviewEvaluator.answerPercent(mixed)).isBetween(75.0, 85.0);
    }

    @Test
    void plainImprovementsAlwaysGiveAtLeastTwoConcretePoints() {
        AnswerEvaluation mid = AnswerEvaluation.builder().content(3).relevance(3).communication(3).confidence(3).build();
        assertThat(InterviewEvaluator.plainImprovements(2, 1, List.of(mid))).hasSize(3).anyMatch(s -> s.contains("skipped")).anyMatch(s -> s.contains("Finish"));
        assertThat(InterviewEvaluator.plainImprovements(0, 0, List.of())).hasSizeGreaterThanOrEqualTo(1);
    }
}
