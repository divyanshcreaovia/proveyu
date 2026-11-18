package com.proveyu.student.interview.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.college.application.CollegeDirectory;
import com.proveyu.student.application.StudentPortalService;
import com.proveyu.college.application.StudentService;
import com.proveyu.college.domain.CollegeBranch;
import com.proveyu.college.domain.CollegeCompany;
import com.proveyu.college.domain.CollegeDrive;
import com.proveyu.college.domain.CollegeEnums.ApplicationStatus;
import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.infrastructure.CollegeDriveRepository;
import com.proveyu.college.infrastructure.CollegeStudentRepository;
import com.proveyu.student.interview.domain.*;
import com.proveyu.student.interview.domain.InterviewModel.*;
import com.proveyu.student.interview.infrastructure.*;
import com.proveyu.shared.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI mock interview for students: plans questions from the role / drive, takes voice or typed answers one at a time, asks
 * follow-ups, and hands answers to {@link InterviewEvaluator} for scoring. Slow AI calls run outside database transactions.
 */
@Slf4j
@Service
public class AiInterviewService {

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final AnswerEvaluationRepository evaluations;
    private final InterviewReportRepository reports;
    private final InterviewCreditRepository credits;
    private final CollegeStudentRepository studentRepository;
    private final CollegeDriveRepository driveRepository;
    private final CollegeDirectory directory;
    private final StudentPortalService portal;
    private final AiGateway ai;
    private final InterviewEvaluator evaluator;
    private final InterviewAudioStore audio;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final InterviewPurchaseService purchases;
    /** Days between free interviews: application.yml default, changeable by the platform admin. */
    private final com.proveyu.shared.settings.PlatformSettings settings;

    private int freeEveryDays() {
        return Math.max(1, settings.number(com.proveyu.shared.settings.PlatformSettings.INTERVIEW_FREE_EVERY_DAYS));
    }
    private final int questionCount;
    private final int maxFollowUps;
    private final int maxAnswerSeconds;
    private final int audioRetentionDays;
    static final int MAX_JD_CHARS = 5000, MIN_JD_CHARS = 40;

    public AiInterviewService(InterviewSessionRepository sessions, InterviewQuestionRepository questions, InterviewAnswerRepository answers,
                            AnswerEvaluationRepository evaluations, InterviewReportRepository reports, InterviewCreditRepository credits,
                            CollegeStudentRepository studentRepository, CollegeDriveRepository driveRepository, CollegeDirectory directory,
                            StudentPortalService portal, AiGateway ai, InterviewEvaluator evaluator, InterviewAudioStore audio, ObjectMapper json,
                            PlatformTransactionManager txm, InterviewPurchaseService purchases,
                            com.proveyu.shared.settings.PlatformSettings settings,
                            @Value("${interview.questions:6}") int questionCount,
                            @Value("${interview.follow-ups:1}") int maxFollowUps,
                            @Value("${interview.max-answer-seconds:180}") int maxAnswerSeconds,
                            @Value("${interview.audio-retention-days:30}") int audioRetentionDays) {
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
        this.evaluations = evaluations;
        this.reports = reports;
        this.credits = credits;
        this.studentRepository = studentRepository;
        this.driveRepository = driveRepository;
        this.directory = directory;
        this.portal = portal;
        this.ai = ai;
        this.evaluator = evaluator;
        this.audio = audio;
        this.json = json;
        this.tx = new TransactionTemplate(txm);
        this.purchases = purchases;
        this.settings = settings;
        this.questionCount = Math.max(2, Math.min(10, questionCount));
        this.maxFollowUps = maxFollowUps;
        this.maxAnswerSeconds = maxAnswerSeconds;
        this.audioRetentionDays = audioRetentionDays;
    }

    // ================================================================== home

    public Overview overview(UUID userId) {
        CollegeStudent s = portal.me(userId);
        List<InterviewSession> mine = sessions.findAllByUserIdOrderByStartedAtDesc(userId);
        Map<UUID, String> companies = companyNames(s.getCollegeId(), mine.stream().map(InterviewSession::getDriveId).filter(Objects::nonNull).toList());
        List<HistoryItem> history = mine.stream().map(x -> {
            InterviewReport r = x.getStatus() == SessionStatus.COMPLETED ? reports.findBySessionId(x.getId()).orElse(null) : null;
            int answered = answers.findAllBySessionId(x.getId()).size();
            return new HistoryItem(x.getId(), x.getInterviewType(), level(x), x.getRoleTitle(),
                    x.getDriveId() == null ? null : companies.get(x.getDriveId()), x.getStatus(), x.getStartedAt(),
                    x.getEndedAt(), answered, r == null ? null : r.getOverallScore(), r == null ? null : r.getRecommendation());
        }).toList();
        List<DriveOption> drives = portal.drives(userId).stream()
                .filter(d -> (d.applicationId() != null && d.applicationStatus() != ApplicationStatus.WITHDRAWN && d.applicationStatus() != ApplicationStatus.REJECTED) || d.canApply())
                .map(d -> new DriveOption(d.id(), d.title(), d.companyName(), d.roleTitle(), d.applicationId() != null)).toList();
        UUID open = mine.stream().filter(x -> x.getStatus() == SessionStatus.IN_PROGRESS).map(InterviewSession::getId).findFirst().orElse(null);
        return new Overview(credits(s), ai.health(), drives, history, open, purchases.recent(userId));
    }

    // ================================================================== start

    public SessionView start(UUID userId, StartRequest req) {
        if (req == null || req.type() == null) throw bad("Choose an interview type", "INVALID_INTERVIEW");
        if (!req.consent()) throw bad("Please agree to the recording and AI evaluation to start", "CONSENT_REQUIRED");
        CollegeStudent s = portal.me(userId);
        InterviewSession open = openSession(userId);
        if (open != null) return view(open);
        Credits c = credits(s);
        if (!c.canStart()) {
            String when = c.nextFreeAt() == null ? "" : " Your next free interview unlocks on "
                    + java.time.format.DateTimeFormatter.ofPattern("d MMM").withZone(java.time.ZoneId.of("Asia/Kolkata")).format(c.nextFreeAt()) + ".";
            throw new DomainException("You have used your free AI interview." + when + " You can also buy one for Rs " + c.pricePerInterview() + ".",
                    HttpStatus.PAYMENT_REQUIRED, "NO_INTERVIEW_CREDITS");
        }
        Difficulty difficulty = req.difficulty() == null ? Difficulty.MEDIUM : req.difficulty();
        String pastedJd = req.driveId() != null || req.jobDescription() == null || req.jobDescription().isBlank() ? null : req.jobDescription().trim();
        if (pastedJd != null && pastedJd.length() > MAX_JD_CHARS) throw bad("The job description is too long. Paste up to " + MAX_JD_CHARS + " characters.", "JD_TOO_LONG");
        if (pastedJd != null && pastedJd.length() < MIN_JD_CHARS) {
            throw bad("Paste the full job description (a few lines with the skills), or leave it empty and just type the role.", "JD_TOO_SHORT");
        }

        InterviewPrompts.Context ctx = context(s, req, difficulty, pastedJd);
        List<InterviewPrompts.PlannedQuestion> plan;
        try {
            plan = InterviewPrompts.readPlan(ai.chatJson(InterviewPrompts.planSystem(), InterviewPrompts.planUser(ctx, questionCount - 1), 0.7,
                    InterviewPrompts.planSchema(questionCount - 1)),
                    req.type(), questionCount, ctx.stack());
        } catch (DomainException e) {
            if (!"AI_BAD_REPLY".equals(e.getErrorCode())) throw e; // service down: do not charge, do not start
            plan = InterviewPrompts.readPlan(null, req.type(), questionCount, ctx.stack());
        }
        final List<InterviewPrompts.PlannedQuestion> finalPlan = plan;
        try {
            InterviewSession created = tx.execute(st -> {
                if (openSession(userId) != null) throw new DomainException("An interview is already in progress", HttpStatus.CONFLICT, "INTERVIEW_IN_PROGRESS");
                CreditSource source = spend(s);
                Instant now = Instant.now();
                InterviewSession x = sessions.save(InterviewSession.builder().collegeId(s.getCollegeId()).studentId(s.getId()).userId(userId)
                        .driveId(req.driveId()).interviewType(req.type()).difficulty(difficulty).roleTitle(ctx.roleTitle()).status(SessionStatus.IN_PROGRESS)
                        .plannedQuestions(finalPlan.size()).jobDescription(pastedJd).focusSkills(blankToNull(InterviewPrompts.cut(String.join(", ", ctx.stack()), 480)))
                        .creditSource(source).consentAt(now).startedAt(now).build());
                for (int i = 0; i < finalPlan.size(); i++) {
                    questions.save(InterviewQuestion.builder().sessionId(x.getId()).seq((i + 1) * 100).questionText(finalPlan.get(i).text())
                            .topic(blankToNull(finalPlan.get(i).topic())).askedAt(i == 0 ? now : null).build());
                }
                return x;
            });
            return view(created);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw new DomainException("Your interview is already starting in another tab", HttpStatus.CONFLICT, "INTERVIEW_IN_PROGRESS");
        }
    }

    // ================================================================== run

    public SessionView session(UUID userId, UUID sessionId) {
        return view(own(userId, sessionId));
    }

    public SessionView answerVoice(UUID userId, UUID sessionId, UUID questionId, byte[] wav, Integer durationSeconds) {
        InterviewSession s = openOwn(userId, sessionId);
        InterviewQuestion q = current(s, questionId);
        if (wav == null || wav.length < 2000) throw bad("The recording is empty. Check your microphone and try again.", "NO_SPEECH");
        if (wav.length > 12_000_000) throw bad("The answer is too long. Keep answers under " + maxAnswerSeconds / 60 + " minutes.", "ANSWER_TOO_LONG");
        String transcript = ai.transcribe(wav);
        if (InterviewPrompts.words(transcript) == 0) {
            throw new DomainException("We could not hear any words. Check your microphone, speak a little louder, and try again.", HttpStatus.UNPROCESSABLE_ENTITY, "NO_SPEECH");
        }
        String path = audio.saveAnswer(sessionId, q.getId(), wav);
        return record(s, q, transcript, InputMode.VOICE, path, durationSeconds);
    }

    /** Typed answer (microphone not available), or a skip when the text is empty. */
    public SessionView answerText(UUID userId, UUID sessionId, TextAnswer body) {
        InterviewSession s = openOwn(userId, sessionId);
        InterviewQuestion q = current(s, body == null ? null : body.questionId());
        String text = body.text() == null ? "" : body.text().trim();
        if (text.length() > 4000) throw bad("Keep the answer under 4000 characters", "ANSWER_TOO_LONG");
        return record(s, q, text, InputMode.TEXT, null, null);
    }

    /** Ends the interview now. With no answers at all the interview is removed and the credit is returned. */
    public SessionView end(UUID userId, UUID sessionId) {
        InterviewSession s = openOwn(userId, sessionId);
        if (answers.findAllBySessionId(s.getId()).isEmpty()) {
            tx.executeWithoutResult(st -> {
                refund(s);
                sessions.deleteById(s.getId());
            });
            audio.deleteSession(s.getId());
            return null;
        }
        finish(s.getId());
        return view(sessions.findById(s.getId()).orElseThrow());
    }

    public ReportView report(UUID userId, UUID sessionId) {
        InterviewSession s = own(userId, sessionId);
        boolean ended = s.getStatus() != SessionStatus.IN_PROGRESS;
        List<InterviewQuestion> qs = questions.findAllBySessionIdOrderBySeq(s.getId());
        Map<UUID, InterviewAnswer> byQuestion = answers.findAllBySessionId(s.getId()).stream()
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, Function.identity()));
        Map<UUID, AnswerEvaluation> evals = evaluations.findAllByAnswerIdIn(byQuestion.values().stream().map(InterviewAnswer::getId).toList()).stream()
                .collect(Collectors.toMap(AnswerEvaluation::getAnswerId, Function.identity()));
        List<AnswerReview> rows = new ArrayList<>();
        int n = 0, answeredCount = 0, skippedCount = 0, notReached = 0;
        double[] sums = new double[4];
        for (InterviewQuestion q : qs) {
            InterviewAnswer a = byQuestion.get(q.getId());
            if (a == null) {
                // Not answered: after the end it is listed as not reached (0); while the interview runs, upcoming questions stay hidden.
                if (!ended) continue;
                notReached++;
                rows.add(new AnswerReview(q.getId(), ++n, q.getParentQuestionId() != null, q.getQuestionText(), q.getTopic(), AnswerStatus.NOT_REACHED,
                        null, null, null, null, false, null, 0, 0, 0, 0, 0, "Not reached: the interview was ended before this question, so it scores 0.", null));
                continue;
            }
            boolean skipped = InterviewPrompts.words(a.getTranscript()) == 0;
            if (skipped) skippedCount++; else answeredCount++;
            AnswerEvaluation e = evals.get(a.getId());
            if (e != null) {
                sums[0] += e.getContent(); sums[1] += e.getRelevance(); sums[2] += e.getCommunication(); sums[3] += e.getConfidence();
            }
            rows.add(new AnswerReview(q.getId(), ++n, q.getParentQuestionId() != null, q.getQuestionText(), q.getTopic(),
                    skipped ? AnswerStatus.SKIPPED : AnswerStatus.ANSWERED, a.getTranscript(),
                    a.getInputMode(), a.getDurationSeconds(), a.getWordsPerMinute(), a.getAudioPath() != null, a.getId(),
                    e == null ? null : e.getContent(), e == null ? null : e.getCommunication(), e == null ? null : e.getConfidence(),
                    e == null ? null : e.getRelevance(), e == null ? null : (int) Math.round(InterviewEvaluator.answerPercent(e)),
                    e == null ? null : e.getFeedback(), e == null ? null : e.getBetterAnswer()));
        }
        int planned = ended ? Math.max(s.getPlannedQuestions(), rows.size()) : s.getPlannedQuestions();
        Criteria criteria = planned == 0 ? new Criteria(0, 0, 0, 0)
                : new Criteria(round1(sums[0] / planned), round1(sums[1] / planned), round1(sums[2] / planned), round1(sums[3] / planned));
        Long duration = s.getEndedAt() == null ? null : Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds();
        InterviewReport r = reports.findBySessionId(s.getId()).orElse(null);
        return new ReportView(s.getId(), s.getInterviewType(), level(s), s.getRoleTitle(), companyName(s), s.getStatus(), s.getStartedAt(), s.getEndedAt(),
                duration, evals.size(), byQuestion.size(), planned, answeredCount, skippedCount, notReached, focus(s), s.getJobDescription() != null,
                r == null ? null : r.getOverallScore(), r == null ? null : r.getRecommendation(), criteria, r == null ? null : r.getSummary(),
                r == null ? List.of() : readList(r.getStrengthsJson()), r == null ? List.of() : readList(r.getImprovementsJson()), rows, s.getErrorMessage());
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** The technologies the interview's questions were limited to. */
    static List<String> focus(InterviewSession s) {
        if (s.getFocusSkills() == null || s.getFocusSkills().isBlank()) return List.of();
        return Arrays.stream(s.getFocusSkills().split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** Scoring failed earlier (model was down): try again. */
    public ReportView retryReport(UUID userId, UUID sessionId) {
        InterviewSession s = own(userId, sessionId);
        if (s.getStatus() == SessionStatus.FAILED) {
            tx.executeWithoutResult(st -> sessions.findById(s.getId()).ifPresent(x -> {
                x.setStatus(SessionStatus.EVALUATING);
                x.setErrorMessage(null);
                sessions.save(x);
            }));
            evaluator.finishLater(s.getId());
        }
        return report(userId, sessionId);
    }

    public byte[] questionSpeech(UUID userId, UUID questionId) {
        InterviewQuestion q = questions.findById(questionId).orElseThrow(() -> notFound("Question"));
        own(userId, q.getSessionId());
        if (q.getAskedAt() == null) throw notFound("Question"); // planned questions stay hidden until asked
        return audio.speech(q.getSessionId(), q.getId(), () -> ai.speak(q.getQuestionText()));
    }

    public byte[] answerAudio(UUID userId, UUID answerId) {
        InterviewAnswer a = answers.findById(answerId).orElseThrow(() -> notFound("Answer"));
        own(userId, a.getSessionId());
        if (a.getAudioPath() == null) throw notFound("Recording");
        return audio.read(a.getAudioPath());
    }

    // ================================================================== admin

    public GrantResult grant(GrantRequest req) {
        if (req == null || req.studentEmail() == null || req.count() < 1 || req.count() > 1000) throw bad("Give a student e-mail and 1 to 1000 interviews", "INVALID_GRANT");
        CollegeStudent s = studentRepository.findAllByEmailIgnoreCaseAndDeletedAtIsNull(req.studentEmail().trim()).stream().findFirst()
                .orElseThrow(() -> notFound("Student"));
        InterviewCredit c = tx.execute(st -> {
            InterviewCredit x = credits.findById(s.getId()).orElseGet(() -> InterviewCredit.builder().studentId(s.getId()).collegeId(s.getCollegeId()).build());
            x.setPaidBalance(x.getPaidBalance() + req.count());
            return credits.save(x);
        });
        return new GrantResult(CollegeDirectory.fullName(s), c.getPaidBalance());
    }

    // ================================================================== background upkeep

    /** After a restart: reports that were being written when the server stopped are queued again. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeEvaluations() {
        sessions.findAllByStatus(SessionStatus.EVALUATING).forEach(s -> evaluator.finishLater(s.getId()));
    }

    /** Every 15 minutes: interviews left open for 3 hours are ended (or removed with the credit returned if nothing was answered). */
    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void closeAbandoned() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(3));
        for (InterviewSession s : sessions.findAllByStatus(SessionStatus.IN_PROGRESS)) {
            if (s.getStartedAt().isAfter(cutoff)) continue;
            try {
                end(s.getUserId(), s.getId());
            } catch (RuntimeException e) {
                log.warn("Could not close abandoned interview {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    /** Daily: answer recordings older than the retention period are deleted; transcripts and scores stay. */
    @Scheduled(cron = "${interview.audio-cleanup-cron:0 30 3 * * *}")
    public void deleteOldAudio() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(audioRetentionDays));
        for (InterviewAnswer a : answers.findAllByAudioPathIsNotNullAndAnsweredAtBefore(cutoff)) {
            audio.delete(a.getAudioPath());
            tx.executeWithoutResult(st -> answers.findById(a.getId()).ifPresent(x -> {
                x.setAudioPath(null);
                answers.save(x);
            }));
        }
    }

    // ================================================================== internals

    private SessionView record(InterviewSession s, InterviewQuestion q, String transcript, InputMode mode, String audioPath, Integer seconds) {
        Integer duration = seconds == null ? null : Math.max(1, Math.min(seconds, maxAnswerSeconds + 30));
        Integer wpm = duration == null || duration < 3 ? null : (int) Math.round(InterviewPrompts.words(transcript) * 60.0 / duration);
        InterviewAnswer saved;
        try {
            saved = tx.execute(st -> answers.save(InterviewAnswer.builder().sessionId(s.getId()).questionId(q.getId()).transcript(transcript)
                    .inputMode(mode).audioPath(audioPath).durationSeconds(duration).wordsPerMinute(wpm).answeredAt(Instant.now()).build()));
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("This question was already answered", HttpStatus.CONFLICT, "ALREADY_ANSWERED");
        }

        // The total number of questions is fixed when the interview starts ("Question 3 of 6" never changes): the follow-up takes the
        // place of the last main question not yet asked. It comes after the first real answer to a question other than the
        // introduction, while at least two main questions are still waiting, so the next topic is kept.
        String followUp = null;
        List<InterviewQuestion> all = questions.findAllBySessionIdOrderBySeq(s.getId());
        long followUpsSoFar = all.stream().filter(x -> x.getParentQuestionId() != null).count();
        long mainsWaiting = all.stream().filter(x -> x.getParentQuestionId() == null && x.getAskedAt() == null).count();
        boolean intro = all.stream().filter(x -> x.getParentQuestionId() == null).findFirst().map(x -> x.getId().equals(q.getId())).orElse(false);
        if (q.getParentQuestionId() == null && !intro && followUpsSoFar < maxFollowUps && mainsWaiting >= 2 && InterviewPrompts.words(transcript) >= 3) {
            List<String> stack = focus(s);
            try {
                followUp = InterviewPrompts.readFollowUp(ai.chatJson(InterviewPrompts.followUpSystem(),
                        InterviewPrompts.followUpUser(s.getRoleTitle(), level(s), stack, q.getQuestionText(), transcript), 0.5, InterviewPrompts.FOLLOW_UP_SCHEMA),
                        q.getQuestionText(), stack);
            } catch (DomainException e) {
                log.info("Follow-up for {} from the model failed: {}", q.getId(), e.getMessage());
            }
            if (followUp == null) followUp = InterviewPrompts.GENERIC_FOLLOW_UP; // every interview gets its one follow-up
        }
        final String fu = followUp;
        boolean finished = Boolean.TRUE.equals(tx.execute(st -> {
            if (fu != null) {
                questions.findAllBySessionIdOrderBySeq(s.getId()).stream()
                        .filter(x -> x.getParentQuestionId() == null && x.getAskedAt() == null)
                        .reduce((a, b) -> b).ifPresent(questions::delete); // its slot goes to the follow-up
                questions.save(InterviewQuestion.builder().sessionId(s.getId()).seq(q.getSeq() + 1).questionText(fu).topic(q.getTopic())
                        .parentQuestionId(q.getId()).build());
            }
            InterviewQuestion next = nextUnanswered(s.getId());
            if (next == null) return true;
            if (next.getAskedAt() == null) {
                next.setAskedAt(Instant.now());
                questions.save(next);
            }
            return false;
        }));
        evaluator.evaluateLater(saved.getId());
        if (finished) finish(s.getId());
        return view(sessions.findById(s.getId()).orElseThrow());
    }

    private void finish(UUID sessionId) {
        tx.executeWithoutResult(st -> sessions.findById(sessionId).ifPresent(x -> {
            if (x.getStatus() != SessionStatus.IN_PROGRESS) return;
            x.setStatus(SessionStatus.EVALUATING);
            x.setEndedAt(Instant.now());
            sessions.save(x);
        }));
        evaluator.finishLater(sessionId);
    }

    private SessionView view(InterviewSession s) {
        List<InterviewQuestion> qs = questions.findAllBySessionIdOrderBySeq(s.getId());
        Map<UUID, InterviewAnswer> byQuestion = answers.findAllBySessionId(s.getId()).stream()
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, Function.identity()));
        List<AskedQuestion> done = new ArrayList<>();
        AskedQuestion current = null;
        int n = 0;
        for (InterviewQuestion q : qs) {
            InterviewAnswer a = byQuestion.get(q.getId());
            if (a != null) {
                done.add(asked(q, ++n, a.getTranscript()));
            } else if (current == null && s.getStatus() == SessionStatus.IN_PROGRESS && q.getAskedAt() != null) {
                current = asked(q, ++n, null);
            }
        }
        return new SessionView(s.getId(), s.getInterviewType(), level(s), s.getRoleTitle(), companyName(s), s.getStatus(), s.getPlannedQuestions(), done.size(),
                s.getStartedAt(), done, current, true, maxAnswerSeconds, focus(s));
    }

    private static AskedQuestion asked(InterviewQuestion q, int number, String transcript) {
        return new AskedQuestion(q.getId(), number, q.getParentQuestionId() != null, q.getQuestionText(), q.getTopic(), transcript);
    }

    private InterviewQuestion nextUnanswered(UUID sessionId) {
        Set<UUID> answered = answers.findAllBySessionId(sessionId).stream().map(InterviewAnswer::getQuestionId).collect(Collectors.toSet());
        return questions.findAllBySessionIdOrderBySeq(sessionId).stream().filter(q -> !answered.contains(q.getId())).findFirst().orElse(null);
    }

    /** The question the student is allowed to answer right now. */
    private InterviewQuestion current(InterviewSession s, UUID questionId) {
        InterviewQuestion next = nextUnanswered(s.getId());
        if (next == null || questionId == null || !next.getId().equals(questionId)) {
            throw new DomainException("This question was already answered. Reload to continue.", HttpStatus.CONFLICT, "ALREADY_ANSWERED");
        }
        return next;
    }

    private static Difficulty level(InterviewSession s) {
        return s.getDifficulty() == null ? Difficulty.MEDIUM : s.getDifficulty();
    }

    /**
     * What the questions are about. The technologies come from the drive (role, job description, skills) or, for a general role,
     * from the role and the pasted job description. With none of those naming a technology, the student's own skills are used, so a
     * student who knows Java is not asked about Python; with nothing at all, the questions stay on general fundamentals.
     */
    private InterviewPrompts.Context context(CollegeStudent s, StartRequest req, Difficulty difficulty, String pastedJd) {
        CollegeBranch b = s.getBranchId() == null ? null : directory.branches(s.getCollegeId()).get(s.getBranchId());
        List<String> studentSkills = StudentService.splitSkills(s.getSkills());
        if (req.driveId() != null) {
            CollegeDrive d = driveRepository.findByIdAndCollegeId(req.driveId(), s.getCollegeId()).filter(x -> x.getDeletedAt() == null)
                    .orElseThrow(() -> notFound("Drive"));
            CollegeCompany c = directory.companies(s.getCollegeId(), Set.of(d.getCompanyId())).get(d.getCompanyId());
            String role = nz(d.getRoleTitle(), d.getTitle());
            List<String> driveSkills = StudentService.splitSkills(d.getSkills());
            List<String> stack = TechStack.detect(role, d.getJobDescription(), String.join(", ", driveSkills));
            if (stack.isEmpty() && (d.getJobDescription() == null || d.getJobDescription().isBlank())) stack = TechStack.detect(String.join(", ", studentSkills));
            return new InterviewPrompts.Context(req.type(), difficulty, role, c == null ? null : c.getName(), d.getJobDescription(),
                    driveSkills, s.getDegree(), b == null ? null : b.getName(), studentSkills, stack);
        }
        String role = req.roleTitle() == null || req.roleTitle().isBlank() ? "Software Engineer (fresher)" : InterviewPrompts.clean(req.roleTitle(), 120);
        List<String> stack = TechStack.detect(req.roleTitle(), pastedJd);
        if (stack.isEmpty() && pastedJd == null) stack = TechStack.detect(String.join(", ", studentSkills));
        return new InterviewPrompts.Context(req.type(), difficulty, role, null, pastedJd, List.of(), s.getDegree(), b == null ? null : b.getName(),
                studentSkills, stack);
    }

    /**
     * One free interview every {@code freeEveryDays()} days. The clock starts when the last free interview ended (or, while it is
     * still being scored, when it started); an interview cancelled with no answers is deleted and does not count. Bought or
     * granted interviews are used once the free one is not available.
     */
    private Credits credits(CollegeStudent s) {
        InterviewCredit c = credits.findById(s.getId()).orElse(null);
        Instant nextFree = nextFreeAt(s.getId());
        boolean freeNow = nextFree != null && !nextFree.isAfter(Instant.now());
        int paid = c == null ? 0 : c.getPaidBalance();
        return new Credits(freeNow ? 1 : 0, paid, freeNow || paid > 0, freeNow ? null : nextFree, freeEveryDays(),
                purchases.priceRupees(), purchases.maxPerPurchase(), purchases.ready());
    }

    /** When the next free interview is available: EPOCH if never used, null while the free interview is still in progress. */
    private Instant nextFreeAt(UUID studentId) {
        // Not Optional.map: a null from map() becomes an empty Optional, which would wrongly mean "never used".
        InterviewSession last = sessions.findFirstByStudentIdAndCreditSourceOrderByStartedAtDesc(studentId, CreditSource.FREE).orElse(null);
        if (last == null) return Instant.EPOCH;
        if (last.getStatus() == SessionStatus.IN_PROGRESS) return null;
        return (last.getEndedAt() != null ? last.getEndedAt() : last.getStartedAt()).plus(Duration.ofDays(freeEveryDays()));
    }

    private CreditSource spend(CollegeStudent s) {
        InterviewCredit c = credits.findById(s.getId()).orElseGet(() -> InterviewCredit.builder().studentId(s.getId()).collegeId(s.getCollegeId()).build());
        Instant nextFree = nextFreeAt(s.getId());
        CreditSource source;
        if (nextFree != null && !nextFree.isAfter(Instant.now())) {
            c.setFreeUsed(c.getFreeUsed() + 1); // lifetime count; also bumps the row version so two tabs cannot both take the free one
            source = CreditSource.FREE;
        } else if (c.getPaidBalance() > 0) {
            c.setPaidBalance(c.getPaidBalance() - 1);
            source = CreditSource.PAID;
        } else {
            throw new DomainException("No AI interview credits left", HttpStatus.PAYMENT_REQUIRED, "NO_INTERVIEW_CREDITS");
        }
        credits.saveAndFlush(c);
        return source;
    }

    private void refund(InterviewSession s) {
        credits.findById(s.getStudentId()).ifPresent(c -> {
            if (s.getCreditSource() == CreditSource.FREE) c.setFreeUsed(Math.max(0, c.getFreeUsed() - 1));
            else c.setPaidBalance(c.getPaidBalance() + 1);
            credits.save(c);
        });
    }

    private InterviewSession openSession(UUID userId) {
        return sessions.findAllByUserIdAndStatus(userId, SessionStatus.IN_PROGRESS).stream().findFirst().orElse(null);
    }

    private InterviewSession own(UUID userId, UUID sessionId) {
        return sessions.findById(sessionId).filter(x -> x.getUserId().equals(userId)).orElseThrow(() -> notFound("Interview"));
    }

    private InterviewSession openOwn(UUID userId, UUID sessionId) {
        InterviewSession s = own(userId, sessionId);
        if (s.getStatus() != SessionStatus.IN_PROGRESS) throw new DomainException("This interview has ended", HttpStatus.CONFLICT, "INTERVIEW_ENDED");
        return s;
    }

    private String companyName(InterviewSession s) {
        return s.getDriveId() == null ? null : companyNames(s.getCollegeId(), List.of(s.getDriveId())).get(s.getDriveId());
    }

    private Map<UUID, String> companyNames(UUID collegeId, Collection<UUID> driveIds) {
        if (driveIds.isEmpty()) return Map.of();
        Map<UUID, CollegeDrive> drives = directory.drives(collegeId, new HashSet<>(driveIds));
        Map<UUID, CollegeCompany> companies = directory.companies(collegeId, drives.values().stream().map(CollegeDrive::getCompanyId).collect(Collectors.toSet()));
        Map<UUID, String> out = new HashMap<>();
        drives.forEach((id, d) -> {
            CollegeCompany c = companies.get(d.getCompanyId());
            if (c != null) out.put(id, c.getName());
        });
        return out;
    }

    private List<String> readList(String raw) {
        try {
            return raw == null ? List.of() : json.readValue(raw, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    private static DomainException bad(String msg, String code) {
        return new DomainException(msg, HttpStatus.BAD_REQUEST, code);
    }

    private static DomainException notFound(String what) {
        return new DomainException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nz(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }
}
