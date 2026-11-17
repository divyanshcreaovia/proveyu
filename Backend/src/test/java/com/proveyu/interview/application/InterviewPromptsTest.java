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
        List<InterviewPrompts.PlannedQuestion> plan = InterviewPrompts.readPlan(reply, InterviewType.TECHNICAL, 5);
        assertThat(plan).hasSize(5);
        assertThat(plan.get(0).text()).containsIgnoringCase("introduce yourself");
        assertThat(plan.get(1).text()).isEqualTo("How does a hash map work internally?"); // numbering stripped, '?' added
        assertThat(plan.stream().map(InterviewPrompts.PlannedQuestion::text).distinct()).hasSize(5);
    }

    @Test
    void planWithoutAnyModelReplyUsesTheFixedBank() {
        assertThat(InterviewPrompts.readPlan(null, InterviewType.HR, 5)).hasSize(5);
        assertThat(InterviewPrompts.readPlan(null, InterviewType.MIXED, 6)).hasSize(6);
    }

    @Test
    void scoresAreClampedToOneToFive() throws Exception {
        InterviewPrompts.Score s = InterviewPrompts.readScore(node("{\"content\":9,\"relevance\":\"0\",\"communication\":3.6,\"confidence\":\"x\",\"feedback\":\" ok \"}"));
        assertThat(List.of(s.content(), s.relevance(), s.communication(), s.confidence())).containsExactly(5, 1, 4, 1);
        assertThat(s.feedback()).isEqualTo("ok");
        assertThat(s.betterAnswer()).isNull();
    }

    @Test
    void followUpRejectsEmptyAndRepeatedQuestions() throws Exception {
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"\"}"), "Q")).isNull();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"None needed\"}"), "Q")).isNull();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"What is a list\"}"), "What is a list")).isNull();
        assertThat(InterviewPrompts.readFollowUp(node("{\"followUp\":\"Which database did you use there\"}"), "Q")).isEqualTo("Which database did you use there?");
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
        for (String s : List.of(InterviewPrompts.planSchema(4), InterviewPrompts.FOLLOW_UP_SCHEMA, InterviewPrompts.SCORE_SCHEMA, InterviewPrompts.SUMMARY_SCHEMA)) {
            assertThat(node(s).path("type").asText()).isEqualTo("object");
        }
        assertThat(node(InterviewPrompts.planSchema(4)).at("/properties/questions/minItems").asInt()).isEqualTo(4);
    }

    @Test
    void answerPercentMapsTheRubricToZeroToHundred() {
        AnswerEvaluation all1 = AnswerEvaluation.builder().content(1).relevance(1).communication(1).confidence(1).build();
        AnswerEvaluation all5 = AnswerEvaluation.builder().content(5).relevance(5).communication(5).confidence(5).build();
        AnswerEvaluation mixed = AnswerEvaluation.builder().content(4).relevance(5).communication(3).confidence(3).build();
        assertThat(InterviewEvaluator.answerPercent(all1)).isZero();
        assertThat(InterviewEvaluator.answerPercent(all5)).isEqualTo(100.0);
        assertThat(InterviewEvaluator.answerPercent(mixed)).isBetween(70.0, 80.0);
    }
}
