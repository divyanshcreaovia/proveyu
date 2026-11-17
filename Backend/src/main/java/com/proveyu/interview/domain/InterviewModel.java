package com.proveyu.interview.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Enums and API shapes of the AI mock interview. */
public final class InterviewModel {
    private InterviewModel() {}

    public enum InterviewType { TECHNICAL, HR, MIXED }
    public enum Difficulty { EASY, MEDIUM, HARD }
    /** PENDING = QR shown; SUBMITTED = UTR entered, waiting for an admin; PAID = interviews added; REJECTED = payment not found. */
    public enum PurchaseStatus { PENDING, SUBMITTED, PAID, REJECTED }
    public enum SessionStatus { IN_PROGRESS, EVALUATING, COMPLETED, FAILED }
    public enum CreditSource { FREE, PAID }
    public enum InputMode { VOICE, TEXT }
    public enum Recommendation { READY, ALMOST_READY, NEEDS_PRACTICE }

    // ---------------------------------------------------------------- requests
    public record StartRequest(InterviewType type, Difficulty difficulty, UUID driveId, String roleTitle, boolean consent) {}
    public record PurchaseRequest(int quantity) {}
    public record UtrRequest(String utr) {}
    public record ReviewRequest(String note) {}
    public record TextAnswer(UUID questionId, String text) {}
    public record GrantRequest(String studentEmail, int count) {}

    // ---------------------------------------------------------------- responses
    /**
     * freeLeft: 1 when the free interview is available now. nextFreeAt: when the next free one unlocks (null when available now,
     * or while the free interview is still in progress). pricePerInterview in rupees.
     */
    public record Credits(int freeLeft, int paidBalance, boolean canStart, Instant nextFreeAt, int freeEveryDays, int pricePerInterview,
                          int maxPerPurchase, boolean paymentsReady) {}
    public record ServiceHealth(boolean llm, boolean speechToText, boolean textToSpeech) {
        public boolean ready() { return llm && speechToText; }
    }
    public record DriveOption(UUID driveId, String title, String companyName, String roleTitle, boolean applied) {}
    public record HistoryItem(UUID id, InterviewType type, Difficulty difficulty, String roleTitle, String companyName, SessionStatus status, Instant startedAt,
                              Instant endedAt, int answered, BigDecimal overallScore, Recommendation recommendation) {}
    public record Overview(Credits credits, ServiceHealth services, List<DriveOption> drives, List<HistoryItem> history, UUID openSessionId,
                           List<StudentPurchase> purchases) {}
    /** A UPI purchase the student can track: waiting for verification, approved or rejected. */
    public record StudentPurchase(UUID id, String reference, int quantity, int amountRupees, PurchaseStatus status, String utr, Instant createdAt,
                                  Instant submittedAt, Instant reviewedAt, String note, String upiUri, String payeeName, String upiId) {}
    /** For the platform admin's payment check. */
    public record AdminPurchase(UUID id, String reference, String studentName, String email, String rollNumber, String collegeName, int quantity,
                                int amountRupees, PurchaseStatus status, String utr, Instant createdAt, Instant submittedAt,
                                Instant reviewedAt, String note) {}

    public record AskedQuestion(UUID id, int number, boolean followUp, String text, String topic, String transcript) {}
    public record SessionView(UUID id, InterviewType type, Difficulty difficulty, String roleTitle, String companyName, SessionStatus status, int plannedQuestions,
                              int mainAnswered, Instant startedAt, List<AskedQuestion> done, AskedQuestion current, boolean speechAvailable,
                              int maxAnswerSeconds) {}

    public record AnswerReview(UUID questionId, int number, boolean followUp, String question, String topic, String transcript, InputMode inputMode,
                               Integer durationSeconds, Integer wordsPerMinute, boolean hasAudio, UUID answerId,
                               Integer content, Integer communication, Integer confidence, Integer relevance, String feedback, String betterAnswer) {}
    public record ReportView(UUID sessionId, InterviewType type, Difficulty difficulty, String roleTitle, String companyName, SessionStatus status, Instant startedAt,
                             Instant endedAt, int evaluated, int total, BigDecimal overallScore, Recommendation recommendation, String summary,
                             List<String> strengths, List<String> improvements, List<AnswerReview> answers, String errorMessage) {}

    public record GrantResult(String studentName, int paidBalance) {}

    /** What the page needs to show the UPI QR: the link with the exact amount, who is paid, and the reference to match it by. */
    public record PurchaseOrder(UUID purchaseId, String reference, int quantity, int amountRupees, String upiUri, String payeeName, String upiId) {}
}
