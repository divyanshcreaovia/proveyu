package com.proveyu.student.prep.application;

import com.proveyu.college.application.CollegeDirectory;
import com.proveyu.college.domain.CollegeBranch;
import com.proveyu.college.domain.CollegeStudent;
import com.proveyu.college.infrastructure.CollegeStudentRepository;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.error.DomainException;
import com.proveyu.student.prep.domain.PrepAttempt;
import com.proveyu.student.prep.domain.PrepModel.AdminAttemptRow;
import com.proveyu.student.prep.domain.PrepModel.CollegeAttemptRow;
import com.proveyu.student.prep.domain.PrepModel.ProctorEventView;
import com.proveyu.student.prep.domain.PrepModel.ProctorLog;
import com.proveyu.student.prep.domain.PrepModel.ProctorStatus;
import com.proveyu.student.prep.domain.PrepProctorEvent;
import com.proveyu.student.prep.domain.PrepTest;
import com.proveyu.student.prep.infrastructure.PrepAttemptRepository;
import com.proveyu.student.prep.infrastructure.PrepProctorEventRepository;
import com.proveyu.student.prep.infrastructure.PrepTestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mock test proctoring. The browser reports every time the student leaves a running test (switches tab or app, or stops sharing
 * the screen) with a screenshot of the shared screen. The count is kept here, not in the browser: the first
 * {@code prep.max-tab-switch-warnings} switches are warnings, the next one submits the test (reason TAB_SWITCH).
 * The student and the placement cell of the student's college can see every switch with its time and screenshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProctorService {

    static final String TAB_SWITCH = "TAB_SWITCH";
    /** The browser may report one switch twice (blur + hidden): reports this close to the last one count once. */
    static final Duration SAME_SWITCH = Duration.ofSeconds(3);
    /** A client time further than this from the server time is not trusted. */
    private static final Duration CLOCK_SKEW = Duration.ofHours(12);

    private final PrepService prep;
    private final PrepAttemptRepository attempts;
    private final PrepTestRepository tests;
    private final PrepProctorEventRepository events;
    private final ProctorScreenshotStore screenshots;
    private final CollegeStudentRepository students;
    private final CollegeDirectory directory;
    private final com.proveyu.college.infrastructure.CollegeProfileRepository colleges;
    private final com.proveyu.shared.settings.PlatformSettings settings;

    // ================================================================== student

    /** Records one switch; on the switch after the last warning the test is submitted. */
    @Transactional
    public ProctorStatus reportSwitch(UUID userId, UUID attemptId, PrepProctorEvent.Kind kind, Instant clientAt, byte[] screenshot) {
        PrepAttempt a = attempts.lockById(attemptId).orElseThrow(() -> notFound("Attempt"));
        if (!a.getUserId().equals(userId)) throw notFound("Attempt");
        if (kind == null) throw new DomainException("Say what happened (kind)", HttpStatus.BAD_REQUEST, "KIND_REQUIRED");
        List<PrepProctorEvent> past = events.findAllByAttemptIdOrderBySeqAsc(attemptId);
        int max = prep.maxTabSwitchWarnings();
        if (a.getSubmittedAt() != null) return status(past.size(), max, true, past.isEmpty() ? null : past.get(past.size() - 1));
        Instant now = Instant.now();
        if (now.isAfter(a.getDeadlineAt())) { // time is up anyway: submit normally, this is not a switch
            prep.autoSubmit(a, null);
            return status(past.size(), max, true, past.isEmpty() ? null : past.get(past.size() - 1));
        }
        PrepProctorEvent last = past.isEmpty() ? null : past.get(past.size() - 1);
        if (last != null && Duration.between(last.getOccurredAt(), now).compareTo(SAME_SWITCH) < 0) {
            return status(past.size(), max, false, last); // the same switch reported twice
        }
        int seq = past.size() + 1;
        PrepProctorEvent e = PrepProctorEvent.builder().id(UUID.randomUUID()).attemptId(attemptId).userId(userId).kind(kind).seq(seq)
                .occurredAt(now).clientAt(trusted(clientAt, now)).build();
        if (screenshot != null && screenshot.length > 0) e.setScreenshotPath(screenshots.save(attemptId, e.getId(), screenshot));
        events.save(e);
        boolean submitted = seq > max;
        if (submitted) prep.autoSubmit(a, TAB_SWITCH);
        return status(seq, max, submitted, e);
    }

    @Transactional(readOnly = true)
    public ProctorLog log(UUID userId, UUID attemptId) {
        return log(prep.own(userId, attemptId));
    }

    @Transactional(readOnly = true)
    public Screenshot screenshot(UUID userId, UUID eventId) {
        PrepProctorEvent e = events.findById(eventId).orElseThrow(() -> notFound("Screenshot"));
        if (!e.getUserId().equals(userId)) throw notFound("Screenshot");
        return read(e);
    }

    // ================================================================== placement cell

    /** Attempts by the college's students (within the member's branch scope), newest first; flaggedOnly = with a switch. */
    @Transactional(readOnly = true)
    public List<CollegeAttemptRow> collegeAttempts(CollegeContext ctx, boolean flaggedOnly) {
        Map<UUID, CollegeStudent> byUser = students.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> s.getUserId() != null && ctx.canAccessBranch(s.getBranchId()))
                .collect(Collectors.toMap(CollegeStudent::getUserId, Function.identity(), (x, y) -> x));
        if (byUser.isEmpty()) return List.of();
        List<PrepAttempt> list = attempts.findAllByUserIdInOrderByStartedAtDesc(byUser.keySet());
        Map<UUID, Integer> switches = prep.switchCounts(list.stream().map(PrepAttempt::getId).toList());
        Map<UUID, String> titles = tests.findAllById(list.stream().map(PrepAttempt::getTestId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PrepTest::getId, PrepTest::getTitle));
        Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
        return list.stream()
                .filter(a -> !flaggedOnly || switches.getOrDefault(a.getId(), 0) > 0)
                .map(a -> {
                    CollegeStudent s = byUser.get(a.getUserId());
                    CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
                    return new CollegeAttemptRow(a.getId(), s.getId(), CollegeDirectory.fullName(s), s.getRollNumber(), b == null ? null : b.getName(),
                            titles.getOrDefault(a.getTestId(), "Deleted test"), a.getStartedAt(), a.getSubmittedAt(), a.getScore(), a.getMaxScore(),
                            switches.getOrDefault(a.getId(), 0), a.getAutoSubmitReason());
                }).toList();
    }

    @Transactional(readOnly = true)
    public ProctorLog collegeLog(CollegeContext ctx, UUID attemptId) {
        PrepAttempt a = attempts.findById(attemptId).orElseThrow(() -> notFound("Attempt"));
        checkCollege(ctx, a.getUserId());
        return log(a);
    }

    @Transactional(readOnly = true)
    public Screenshot collegeScreenshot(CollegeContext ctx, UUID eventId) {
        PrepProctorEvent e = events.findById(eventId).orElseThrow(() -> notFound("Screenshot"));
        checkCollege(ctx, e.getUserId());
        return read(e);
    }

    // ================================================================== platform admin

    /** The latest attempts (up to 500) in which a student left the test, across all colleges. */
    @Transactional(readOnly = true)
    public List<AdminAttemptRow> adminFlagged() {
        List<UUID> ids = events.flaggedAttemptIds(PageRequest.of(0, 500));
        if (ids.isEmpty()) return List.of();
        Map<UUID, PrepAttempt> byId = attempts.findAllById(ids).stream().collect(Collectors.toMap(PrepAttempt::getId, Function.identity()));
        Map<UUID, Integer> switches = prep.switchCounts(ids);
        Map<UUID, String> titles = tests.findAllById(byId.values().stream().map(PrepAttempt::getTestId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PrepTest::getId, PrepTest::getTitle));
        Map<UUID, Optional<CollegeStudent>> studentOf = new HashMap<>();
        byId.values().forEach(a -> studentOf.computeIfAbsent(a.getUserId(), students::findFirstByUserIdAndDeletedAtIsNull));
        Map<UUID, String> collegeNames = new HashMap<>();
        colleges.findAllById(studentOf.values().stream().flatMap(Optional::stream).map(CollegeStudent::getCollegeId).collect(Collectors.toSet()))
                .forEach(c -> collegeNames.put(c.getCollegeId(), c.getCollegeName()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(a -> {
            CollegeStudent s = studentOf.get(a.getUserId()).orElse(null);
            return new AdminAttemptRow(a.getId(), s == null ? null : collegeNames.get(s.getCollegeId()), s == null ? "Unknown student" : CollegeDirectory.fullName(s),
                    s == null ? null : s.getRollNumber(), titles.getOrDefault(a.getTestId(), "Deleted test"), a.getStartedAt(), a.getSubmittedAt(),
                    a.getScore(), a.getMaxScore(), switches.getOrDefault(a.getId(), 0), a.getAutoSubmitReason());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ProctorLog adminLog(UUID attemptId) {
        return log(attempts.findById(attemptId).orElseThrow(() -> notFound("Attempt")));
    }

    @Transactional(readOnly = true)
    public Screenshot adminScreenshot(UUID eventId) {
        return read(events.findById(eventId).orElseThrow(() -> notFound("Screenshot")));
    }

    /** Every night: screenshots older than the retention set by the admin are deleted (0 days = keep them). The switch rows stay. */
    @Scheduled(cron = "${prep.proctor-cleanup-cron:0 45 3 * * *}")
    @Transactional
    public void deleteOldScreenshots() {
        int days = settings.number(com.proveyu.shared.settings.PlatformSettings.PREP_PROCTOR_RETENTION_DAYS);
        if (days <= 0) return;
        int n = 0;
        for (PrepProctorEvent e : events.findAllByScreenshotPathNotNullAndOccurredAtBefore(Instant.now().minus(Duration.ofDays(days)))) {
            try {
                screenshots.delete(e.getScreenshotPath());
                e.setScreenshotPath(null);
                events.save(e);
                n++;
            } catch (RuntimeException ex) {
                log.warn("Could not delete proctoring screenshot {}: {}", e.getId(), ex.getMessage());
            }
        }
        if (n > 0) log.info("Deleted {} proctoring screenshot(s) older than {} days", n, days);
    }

    // ================================================================== helpers

    public record Screenshot(byte[] bytes, String contentType) {}

    private ProctorLog log(PrepAttempt a) {
        return new ProctorLog(a.getId(), a.getAutoSubmitReason(), prep.maxTabSwitchWarnings(),
                events.findAllByAttemptIdOrderBySeqAsc(a.getId()).stream().map(ProctorService::view).toList());
    }

    /** Same answer (404) for another college's attempt as for a missing one, so ids cannot be probed. */
    private void checkCollege(CollegeContext ctx, UUID userId) {
        CollegeStudent s = students.findFirstByUserIdAndDeletedAtIsNull(userId).orElseThrow(() -> notFound("Attempt"));
        if (!s.getCollegeId().equals(ctx.collegeId()) || !ctx.canAccessBranch(s.getBranchId())) throw notFound("Attempt");
    }

    private Screenshot read(PrepProctorEvent e) {
        if (e.getScreenshotPath() == null) throw notFound("Screenshot");
        return new Screenshot(screenshots.read(e.getScreenshotPath()), ProctorScreenshotStore.contentType(e.getScreenshotPath()));
    }

    private static ProctorStatus status(int switches, int max, boolean submitted, PrepProctorEvent e) {
        return new ProctorStatus(switches, max, Math.max(0, max - switches), submitted, e == null ? null : view(e));
    }

    private static ProctorEventView view(PrepProctorEvent e) {
        return new ProctorEventView(e.getId(), e.getKind().name(), e.getSeq(), e.getOccurredAt(), e.getScreenshotPath() != null);
    }

    private static Instant trusted(Instant clientAt, Instant now) {
        return clientAt != null && Duration.between(clientAt, now).abs().compareTo(CLOCK_SKEW) < 0 ? clientAt : null;
    }

    private static DomainException notFound(String what) {
        return new DomainException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
