package com.proveyu.interview.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.interview.domain.*;
import com.proveyu.interview.domain.InterviewModel.Recommendation;
import com.proveyu.interview.domain.InterviewModel.SessionStatus;
import com.proveyu.interview.infrastructure.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scores answers and writes the final report in the background, one job at a time, so scoring never slows down the live
 * interview more than the model server allows. Answers are scored as they come in; the final job scores anything missed.
 */
@Slf4j
@Component
public class InterviewEvaluator {

    /** Rubric weights for one answer. Content matters most for hiring; confidence least, since freshers are nervous. */
    private static final double W_CONTENT = 0.40, W_RELEVANCE = 0.25, W_COMMUNICATION = 0.20, W_CONFIDENCE = 0.15;

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final AnswerEvaluationRepository evaluations;
    private final InterviewReportRepository reports;
    private final AiGateway ai;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "interview-eval");
        t.setDaemon(true);
        return t;
    });

    public InterviewEvaluator(InterviewSessionRepository sessions, InterviewQuestionRepository questions, InterviewAnswerRepository answers,
                              AnswerEvaluationRepository evaluations, InterviewReportRepository reports, AiGateway ai, ObjectMapper json,
                              PlatformTransactionManager txm) {
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
        this.evaluations = evaluations;
        this.reports = reports;
        this.ai = ai;
        this.json = json;
        this.tx = new TransactionTemplate(txm);
    }

    public void evaluateLater(UUID answerId) {
        worker.submit(() -> safely(() -> evaluate(answerId)));
    }

    public void finishLater(UUID sessionId) {
        worker.submit(() -> safely(() -> finish(sessionId)));
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }

    // ------------------------------------------------------------------ jobs

    /** @return true when the answer has a score (now or from before). */
    boolean evaluate(UUID answerId) {
        if (!evaluations.findAllByAnswerIdIn(List.of(answerId)).isEmpty()) return true;
        InterviewAnswer a = answers.findById(answerId).orElse(null);
        if (a == null) return true;
        InterviewQuestion q = questions.findById(a.getQuestionId()).orElseThrow();
        InterviewSession s = sessions.findById(a.getSessionId()).orElseThrow();
        InterviewPrompts.Score score;
        String model = ai.evalModel();
        if (InterviewPrompts.words(a.getTranscript()) == 0) {
            score = InterviewPrompts.emptyAnswerScore();
            model = "rule";
        } else {
            score = null;
            for (int attempt = 0; attempt < 2 && score == null; attempt++) {
                try {
                    score = InterviewPrompts.readScore(ai.evalJson(InterviewPrompts.scoreSystem(),
                            InterviewPrompts.scoreUser(s.getRoleTitle(), q.getQuestionText(), a.getTranscript(),
                                    a.getWordsPerMinute() == null ? 0 : a.getWordsPerMinute(), InterviewPrompts.fillerCount(a.getTranscript())), 0.1, InterviewPrompts.SCORE_SCHEMA));
                } catch (RuntimeException e) {
                    log.warn("Scoring answer {} failed (attempt {}): {}", answerId, attempt + 1, e.getMessage());
                }
            }
            if (score == null) return false;
        }
        InterviewPrompts.Score sc = score;
        String m = model;
        try {
            tx.executeWithoutResult(st -> evaluations.save(AnswerEvaluation.builder().answerId(answerId).content(sc.content())
                    .communication(sc.communication()).confidence(sc.confidence()).relevance(sc.relevance()).feedback(sc.feedback())
                    .betterAnswer(sc.betterAnswer()).model(m).build()));
        } catch (DataIntegrityViolationException e) {
            // scored twice at the same time; the first one stays
        }
        return true;
    }

    void finish(UUID sessionId) {
        InterviewSession s = sessions.findById(sessionId).orElse(null);
        if (s == null || s.getStatus() != SessionStatus.EVALUATING) return;
        List<InterviewAnswer> list = answers.findAllBySessionId(sessionId);
        boolean allScored = true;
        for (InterviewAnswer a : list) allScored &= evaluate(a.getId());
        if (!allScored) {
            setStatus(sessionId, SessionStatus.FAILED, "Scoring could not finish because the AI service was busy or offline. Try again in a few minutes.");
            return;
        }

        Map<UUID, InterviewQuestion> qs = questions.findAllBySessionIdOrderBySeq(sessionId).stream()
                .collect(Collectors.toMap(InterviewQuestion::getId, Function.identity()));
        Map<UUID, AnswerEvaluation> evals = evaluations.findAllByAnswerIdIn(list.stream().map(InterviewAnswer::getId).toList()).stream()
                .collect(Collectors.toMap(AnswerEvaluation::getAnswerId, Function.identity()));
        List<InterviewAnswer> ordered = list.stream().sorted(Comparator.comparingInt(a -> qs.get(a.getQuestionId()).getSeq())).toList();

        long mainAnswered = ordered.stream().filter(a -> qs.get(a.getQuestionId()).getParentQuestionId() == null).count();
        int skippedMain = (int) Math.max(0, s.getPlannedQuestions() - mainAnswered); // ended early: unasked questions count as zero
        double sum = 0;
        StringBuilder perQuestion = new StringBuilder();
        int n = 0;
        for (InterviewAnswer a : ordered) {
            AnswerEvaluation e = evals.get(a.getId());
            sum += answerPercent(e);
            InterviewQuestion q = qs.get(a.getQuestionId());
            perQuestion.append("Q").append(++n).append(q.getTopic() == null ? "" : " (" + q.getTopic() + ")").append(": content ").append(e.getContent())
                    .append(", relevance ").append(e.getRelevance()).append(", communication ").append(e.getCommunication()).append(", confidence ")
                    .append(e.getConfidence()).append(". ").append(e.getFeedback() == null ? "" : e.getFeedback()).append('\n');
        }
        if (skippedMain > 0) perQuestion.append(skippedMain).append(" planned question(s) were not answered because the interview was ended early.\n");
        int denominator = ordered.size() + skippedMain;
        BigDecimal overall = BigDecimal.valueOf(denominator == 0 ? 0 : sum / denominator).setScale(2, RoundingMode.HALF_UP);
        Recommendation rec = overall.doubleValue() >= 75 ? Recommendation.READY : overall.doubleValue() >= 55 ? Recommendation.ALMOST_READY : Recommendation.NEEDS_PRACTICE;

        InterviewPrompts.Summary summary;
        try {
            summary = InterviewPrompts.readSummary(ai.evalJson(InterviewPrompts.summarySystem(),
                    InterviewPrompts.summaryUser(s.getRoleTitle(), overall.intValue(), perQuestion.toString()), 0.3, InterviewPrompts.SUMMARY_SCHEMA));
        } catch (RuntimeException e) {
            log.warn("Summary for {} failed, using the plain one: {}", sessionId, e.getMessage());
            summary = new InterviewPrompts.Summary(null, List.of(), List.of());
        }
        if (summary.summary() == null) summary = new InterviewPrompts.Summary(plainSummary(overall.intValue(), rec, ordered.size()), summary.strengths(), summary.improvements());
        InterviewPrompts.Summary sm = summary;
        tx.executeWithoutResult(st -> {
            if (reports.findBySessionId(sessionId).isEmpty()) {
                reports.save(InterviewReport.builder().sessionId(sessionId).overallScore(overall).recommendation(rec).summary(sm.summary())
                        .strengthsJson(write(sm.strengths())).improvementsJson(write(sm.improvements())).model(ai.evalModel()).build());
            }
            sessions.findById(sessionId).ifPresent(x -> {
                x.setStatus(SessionStatus.COMPLETED);
                x.setErrorMessage(null);
                sessions.save(x);
            });
        });
    }

    /** One answer as 0..100 from its four 1..5 scores. */
    static double answerPercent(AnswerEvaluation e) {
        double w = W_CONTENT * e.getContent() + W_RELEVANCE * e.getRelevance() + W_COMMUNICATION * e.getCommunication() + W_CONFIDENCE * e.getConfidence();
        return (w - 1) / 4 * 100;
    }

    private static String plainSummary(int overall, Recommendation rec, int answered) {
        String verdict = switch (rec) {
            case READY -> "You are interview-ready for this role.";
            case ALMOST_READY -> "You are close; a little more practice will make a clear difference.";
            case NEEDS_PRACTICE -> "You need more practice before the real interview.";
        };
        return "You answered " + answered + " question(s) and scored " + overall + " out of 100. " + verdict
                + " Read the feedback under each answer and try again.";
    }

    private void setStatus(UUID id, SessionStatus status, String error) {
        tx.executeWithoutResult(st -> sessions.findById(id).ifPresent(x -> {
            x.setStatus(status);
            x.setErrorMessage(error);
            sessions.save(x);
        }));
    }

    private String write(List<String> list) {
        try {
            return json.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.error("Interview evaluation job failed", e);
        }
    }
}
