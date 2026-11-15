package com.proveyu.college.application;

import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.SlotStatus;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Interview slots with conflict detection (student / panel / room overlap), rescheduling and reminders. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampusInterviewService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE dd MMM yyyy, hh:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final InterviewSlotRepository slotRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final DriveRoundRepository roundRepository;
    private final CollegeDirectory directory;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CollegeMailService mailService;

    public static class ConflictException extends DomainException {
        private final List<Conflict> conflicts;

        public ConflictException(List<Conflict> conflicts) {
            super(conflicts.stream().map(Conflict::message).collect(Collectors.joining("; ")), HttpStatus.CONFLICT, "SCHEDULE_CONFLICT");
            this.conflicts = conflicts;
        }

        public List<Conflict> getConflicts() { return conflicts; }
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> calendar(CollegeContext ctx, Instant from, Instant to) {
        if (Duration.between(from, to).toDays() > 62) throw new DomainException("Range too large (max 62 days)", HttpStatus.BAD_REQUEST, "RANGE_TOO_LARGE");
        return map(ctx, slotRepository.findAllByCollegeIdAndStartsAtBetweenOrderByStartsAtAsc(ctx.collegeId(), from, to));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> forDrive(CollegeContext ctx, UUID driveId) {
        directory.drive(ctx.collegeId(), driveId);
        return map(ctx, slotRepository.findAllByCollegeIdAndDriveIdOrderByStartsAtAsc(ctx.collegeId(), driveId));
    }

    @Transactional(readOnly = true)
    public List<Conflict> conflicts(UUID collegeId, UUID excludeId, UUID applicationId, String panel, String room, Instant start, Instant end) {
        if (!end.isAfter(start)) throw new DomainException("End time must be after start time", HttpStatus.BAD_REQUEST, "BAD_TIME_RANGE");
        CollegeApplication app = directory.application(collegeId, applicationId);
        var overlapping = slotRepository.findOverlapping(collegeId, start, end, excludeId);
        var apps = applicationRepository.findAllByIdInAndCollegeId(overlapping.stream().map(InterviewSlot::getApplicationId).toList(), collegeId)
                .stream().collect(Collectors.toMap(CollegeApplication::getId, Function.identity()));
        List<Conflict> out = new ArrayList<>();
        for (InterviewSlot s : overlapping) {
            CollegeApplication other = apps.get(s.getApplicationId());
            if (other != null && other.getStudentId().equals(app.getStudentId())) {
                out.add(new Conflict("STUDENT", "Student already has an interview at " + WHEN.format(s.getStartsAt()), s.getId()));
            }
            if (panel != null && !panel.isBlank() && panel.equalsIgnoreCase(s.getPanelName())) {
                out.add(new Conflict("PANEL", "Panel '" + panel + "' is busy at " + WHEN.format(s.getStartsAt()), s.getId()));
            }
            if (room != null && !room.isBlank() && room.equalsIgnoreCase(s.getRoom())) {
                out.add(new Conflict("ROOM", "Room '" + room + "' is booked at " + WHEN.format(s.getStartsAt()), s.getId()));
            }
        }
        return out;
    }

    @Transactional
    public SlotResponse schedule(CollegeContext ctx, SlotRequest r) {
        CollegeDrive drive = directory.drive(ctx.collegeId(), r.driveId());
        CollegeApplication app = directory.application(ctx.collegeId(), r.applicationId());
        if (!app.getDriveId().equals(drive.getId())) throw new DomainException("Application does not belong to this drive", HttpStatus.BAD_REQUEST, "APP_MISMATCH");
        if (r.roundId() != null && !directory.round(ctx.collegeId(), r.roundId()).getDriveId().equals(drive.getId())) {
            throw new DomainException("Round does not belong to this drive", HttpStatus.BAD_REQUEST, "ROUND_MISMATCH");
        }
        var conflicts = conflicts(ctx.collegeId(), null, app.getId(), r.panelName(), r.room(), r.startsAt(), r.endsAt());
        if (!conflicts.isEmpty()) throw new ConflictException(conflicts);
        InterviewSlot slot = slotRepository.save(InterviewSlot.builder().collegeId(ctx.collegeId()).driveId(drive.getId()).roundId(r.roundId())
                .applicationId(app.getId()).panelName(r.panelName()).panelMembers(r.panelMembers()).startsAt(r.startsAt()).endsAt(r.endsAt())
                .room(r.room()).meetingLink(r.meetingLink()).build());
        notifyStudent(ctx.collegeId(), slot, drive, "INTERVIEW_SCHEDULED");
        auditService.log(ctx, "INTERVIEW_SCHEDULED", "DRIVE", drive.getId(), "Scheduled interview at " + WHEN.format(slot.getStartsAt()), null);
        return map(ctx, List.of(slot)).get(0);
    }

    /** Creates back-to-back slots for many applicants; returns created slots and skips conflicting ones. */
    @Transactional
    public Map<String, Object> bulkSchedule(CollegeContext ctx, BulkSlotRequest r) {
        CollegeDrive drive = directory.drive(ctx.collegeId(), r.driveId());
        Instant cursor = r.startAt();
        Duration dur = Duration.ofMinutes(r.durationMinutes());
        Duration gap = Duration.ofMinutes(r.gapMinutes() == null ? 0 : r.gapMinutes());
        List<SlotResponse> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (UUID appId : r.applicationIds()) {
            try {
                created.add(schedule(ctx, new SlotRequest(drive.getId(), r.roundId(), appId, r.panelName(), r.panelMembers(), cursor, cursor.plus(dur),
                        r.room(), r.meetingLink())));
                cursor = cursor.plus(dur).plus(gap);
            } catch (DomainException e) {
                skipped.add(appId + ": " + e.getMessage());
            }
        }
        return Map.of("created", created, "skipped", skipped);
    }

    @Transactional
    public SlotResponse reschedule(CollegeContext ctx, UUID slotId, SlotRequest r) {
        InterviewSlot slot = slotRepository.findByIdAndCollegeId(slotId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Interview"));
        if (slot.getStatus() != SlotStatus.SCHEDULED) throw new DomainException("Only scheduled interviews can be changed", HttpStatus.CONFLICT, "SLOT_CLOSED");
        var conflicts = conflicts(ctx.collegeId(), slotId, slot.getApplicationId(), r.panelName(), r.room(), r.startsAt(), r.endsAt());
        if (!conflicts.isEmpty()) throw new ConflictException(conflicts);
        boolean timeChanged = !slot.getStartsAt().equals(r.startsAt()) || !slot.getEndsAt().equals(r.endsAt());
        slot.setPanelName(r.panelName());
        slot.setPanelMembers(r.panelMembers());
        slot.setRoom(r.room());
        slot.setMeetingLink(r.meetingLink());
        slot.setStartsAt(r.startsAt());
        slot.setEndsAt(r.endsAt());
        if (timeChanged) {
            slot.setRescheduleCount(slot.getRescheduleCount() + 1);
            slot.setReminderSentAt(null);
        }
        slotRepository.save(slot);
        CollegeDrive drive = directory.drive(ctx.collegeId(), slot.getDriveId());
        if (timeChanged) notifyStudent(ctx.collegeId(), slot, drive, "INTERVIEW_SCHEDULED");
        auditService.log(ctx, "INTERVIEW_RESCHEDULED", "DRIVE", slot.getDriveId(), "Rescheduled interview to " + WHEN.format(slot.getStartsAt()), null);
        return map(ctx, List.of(slot)).get(0);
    }

    @Transactional
    public SlotResponse setStatus(CollegeContext ctx, UUID slotId, SlotStatus status) {
        InterviewSlot slot = slotRepository.findByIdAndCollegeId(slotId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Interview"));
        slot.setStatus(status);
        slotRepository.save(slot);
        if (status == SlotStatus.CANCELLED) {
            CollegeApplication app = directory.application(ctx.collegeId(), slot.getApplicationId());
            CollegeStudent s = directory.student(ctx.collegeId(), app.getStudentId());
            notificationService.notify(s.getUserId(), "INTERVIEW", "Your interview on " + WHEN.format(slot.getStartsAt()) + " has been cancelled.");
        }
        auditService.log(ctx, "INTERVIEW_" + status, "DRIVE", slot.getDriveId(), "Interview marked " + status, null);
        return map(ctx, List.of(slot)).get(0);
    }

    @Transactional
    public SlotResponse remind(CollegeContext ctx, UUID slotId) {
        InterviewSlot slot = slotRepository.findByIdAndCollegeId(slotId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Interview"));
        notifyStudent(ctx.collegeId(), slot, directory.drive(ctx.collegeId(), slot.getDriveId()), "INTERVIEW_REMINDER");
        slot.setReminderSentAt(Instant.now());
        slotRepository.save(slot);
        return map(ctx, List.of(slot)).get(0);
    }

    /** Every 15 minutes: remind students of interviews starting in the next 24 hours. */
    @Scheduled(fixedDelayString = "${college.reminders.interval-ms:900000}", initialDelay = 60000)
    @Transactional
    public void sendDueReminders() {
        Instant now = Instant.now();
        var due = slotRepository.findAllByStatusAndReminderSentAtIsNullAndStartsAtBetween(SlotStatus.SCHEDULED, now, now.plus(Duration.ofHours(24)));
        for (InterviewSlot slot : due) {
            try {
                notifyStudent(slot.getCollegeId(), slot, directory.drive(slot.getCollegeId(), slot.getDriveId()), "INTERVIEW_REMINDER");
                slot.setReminderSentAt(now);
                slotRepository.save(slot);
            } catch (Exception e) {
                log.warn("[INTERVIEW REMINDER] slot {} failed: {}", slot.getId(), e.getMessage());
            }
        }
        if (!due.isEmpty()) log.info("[INTERVIEW REMINDER] sent {} reminder(s)", due.size());
    }

    private void notifyStudent(UUID collegeId, InterviewSlot slot, CollegeDrive drive, String template) {
        CollegeApplication app = directory.application(collegeId, slot.getApplicationId());
        CollegeStudent s = directory.student(collegeId, app.getStudentId());
        CollegeCompany c = directory.company(collegeId, drive.getCompanyId());
        String where = slot.getMeetingLink() != null && !slot.getMeetingLink().isBlank() ? slot.getMeetingLink()
                : Objects.toString(slot.getRoom(), Objects.toString(drive.getVenue(), "Will be shared by the placement cell"));
        String when = WHEN.format(slot.getStartsAt());
        notificationService.notify(s.getUserId(), "INTERVIEW", ("INTERVIEW_REMINDER".equals(template) ? "Reminder: " : "") + c.getName()
                + " interview on " + when + " at " + where);
        mailService.send(collegeId, template, s.getEmail(), Map.of("studentName", CollegeDirectory.fullName(s), "companyName", c.getName(),
                "when", when, "where", where));
    }

    private List<SlotResponse> map(CollegeContext ctx, List<InterviewSlot> slots) {
        if (slots.isEmpty()) return List.of();
        var apps = applicationRepository.findAllByIdInAndCollegeId(slots.stream().map(InterviewSlot::getApplicationId).collect(Collectors.toSet()), ctx.collegeId())
                .stream().collect(Collectors.toMap(CollegeApplication::getId, Function.identity()));
        var students = directory.students(ctx.collegeId(), apps.values().stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        var drives = directory.drives(ctx.collegeId(), slots.stream().map(InterviewSlot::getDriveId).collect(Collectors.toSet()));
        var companies = directory.companies(ctx.collegeId(), drives.values().stream().map(CollegeDrive::getCompanyId).collect(Collectors.toSet()));
        Map<UUID, String> roundNames = new HashMap<>();
        slots.stream().map(InterviewSlot::getRoundId).filter(Objects::nonNull).distinct()
                .forEach(id -> roundRepository.findByIdAndCollegeId(id, ctx.collegeId()).ifPresent(r -> roundNames.put(id, r.getName())));
        return slots.stream().map(s -> {
            CollegeApplication a = apps.get(s.getApplicationId());
            CollegeStudent st = a == null ? null : students.get(a.getStudentId());
            CollegeDrive d = drives.get(s.getDriveId());
            if (st != null && !ctx.canAccessBranch(st.getBranchId())) return null;
            return new SlotResponse(s.getId(), s.getDriveId(), d == null ? null : d.getTitle(),
                    d == null || companies.get(d.getCompanyId()) == null ? null : companies.get(d.getCompanyId()).getName(),
                    s.getRoundId(), s.getRoundId() == null ? null : roundNames.get(s.getRoundId()), s.getApplicationId(),
                    st == null ? null : st.getId(), st == null ? null : CollegeDirectory.fullName(st), st == null ? null : st.getRollNumber(),
                    s.getPanelName(), s.getPanelMembers(), s.getStartsAt(), s.getEndsAt(), s.getRoom(), s.getMeetingLink(), s.getStatus(),
                    s.getRescheduleCount(), s.getReminderSentAt());
        }).filter(Objects::nonNull).toList();
    }
}
