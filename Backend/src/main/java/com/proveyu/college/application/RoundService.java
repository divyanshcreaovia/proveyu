package com.proveyu.college.application;

import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.notification.application.NotificationService;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Round pipeline:
 * start(first round) seeds SHORTLISTED applications -> PENDING results, application IN_PROCESS.
 * complete(round) requires every result to be PASS / FAIL / ABSENT, then auto-advances:
 * PASS -> PENDING result in the next round (or application SELECTED after the last round);
 * FAIL / ABSENT -> application REJECTED.
 */
@Service
@RequiredArgsConstructor
public class RoundService {

    private final DriveRoundRepository roundRepository;
    private final RoundResultRepository resultRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeDirectory directory;
    private final CollegeFileService fileService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CollegeMailService mailService;

    @Transactional(readOnly = true)
    public List<RoundResponse> list(CollegeContext ctx, UUID driveId) {
        directory.drive(ctx.collegeId(), driveId);
        return roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(ctx.collegeId(), driveId).stream().map(r -> toResponse(ctx.collegeId(), r)).toList();
    }

    @Transactional
    public RoundResponse add(CollegeContext ctx, UUID driveId, RoundRequest req) {
        CollegeDrive d = directory.drive(ctx.collegeId(), driveId);
        if (d.getStatus() == DriveStatus.COMPLETED || d.getStatus() == DriveStatus.CANCELLED) {
            throw new DomainException("Drive is closed", HttpStatus.CONFLICT, "DRIVE_CLOSED");
        }
        int next = roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(ctx.collegeId(), driveId).stream().mapToInt(DriveRound::getSeq).max().orElse(0) + 1;
        DriveRound r = roundRepository.save(DriveRound.builder().collegeId(ctx.collegeId()).driveId(driveId).seq(next).name(req.name().trim())
                .roundType(req.roundType()).scheduledAt(req.scheduledAt()).build());
        auditService.log(ctx, "ROUND_ADDED", "DRIVE", driveId, "Added round " + next + " (" + r.getName() + ") to " + d.getTitle(), null);
        return toResponse(ctx.collegeId(), r);
    }

    @Transactional
    public RoundResponse update(CollegeContext ctx, UUID roundId, RoundRequest req) {
        DriveRound r = directory.round(ctx.collegeId(), roundId);
        r.setName(req.name().trim());
        r.setRoundType(req.roundType());
        r.setScheduledAt(req.scheduledAt());
        return toResponse(ctx.collegeId(), roundRepository.save(r));
    }

    @Transactional
    public void delete(CollegeContext ctx, UUID roundId) {
        DriveRound r = directory.round(ctx.collegeId(), roundId);
        if (r.getStatus() != RoundStatus.PENDING || !resultRepository.findAllByCollegeIdAndRoundId(ctx.collegeId(), roundId).isEmpty()) {
            throw new DomainException("Only rounds that have not started can be deleted", HttpStatus.CONFLICT, "ROUND_STARTED");
        }
        roundRepository.delete(r);
        auditService.log(ctx, "ROUND_DELETED", "DRIVE", r.getDriveId(), "Deleted round " + r.getName(), null);
    }

    @Transactional
    public List<RoundResponse> reorder(CollegeContext ctx, UUID driveId, ReorderRequest req) {
        var rounds = roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(ctx.collegeId(), driveId);
        Map<UUID, DriveRound> byId = rounds.stream().collect(Collectors.toMap(DriveRound::getId, Function.identity()));
        if (req.roundIds().size() != rounds.size() || !byId.keySet().containsAll(req.roundIds())) {
            throw new DomainException("Provide every round of the drive exactly once", HttpStatus.BAD_REQUEST, "BAD_ORDER");
        }
        if (rounds.stream().anyMatch(r -> r.getStatus() != RoundStatus.PENDING)) {
            throw new DomainException("Rounds cannot be reordered after the pipeline has started", HttpStatus.CONFLICT, "ROUND_STARTED");
        }
        rounds.forEach(r -> { r.setSeq(r.getSeq() + 1000); roundRepository.saveAndFlush(r); });
        for (int i = 0; i < req.roundIds().size(); i++) {
            DriveRound r = byId.get(req.roundIds().get(i));
            r.setSeq(i + 1);
            roundRepository.saveAndFlush(r);
        }
        return list(ctx, driveId);
    }

    @Transactional
    public RoundResponse start(CollegeContext ctx, UUID roundId) {
        DriveRound round = directory.round(ctx.collegeId(), roundId);
        CollegeDrive drive = directory.drive(ctx.collegeId(), round.getDriveId());
        if (round.getStatus() != RoundStatus.PENDING) throw new DomainException("Round already started", HttpStatus.CONFLICT, "ROUND_STARTED");
        if (drive.getStatus() != DriveStatus.PUBLISHED && drive.getStatus() != DriveStatus.ONGOING) {
            throw new DomainException("Publish the drive before starting rounds", HttpStatus.CONFLICT, "DRIVE_NOT_ACTIVE");
        }
        var rounds = roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(ctx.collegeId(), drive.getId());
        int idx = indexOf(rounds, round);
        Map<UUID, CollegeApplication> participants = new LinkedHashMap<>();
        if (idx == 0) {
            applicationRepository.findAllByCollegeIdAndDriveId(ctx.collegeId(), drive.getId()).stream()
                    .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED).forEach(a -> participants.put(a.getId(), a));
            if (participants.isEmpty()) {
                throw new DomainException("Shortlist applicants before starting the first round", HttpStatus.CONFLICT, "NO_SHORTLIST");
            }
            participants.values().forEach(a -> resultRepository.findByRoundIdAndApplicationId(roundId, a.getId())
                    .orElseGet(() -> resultRepository.save(RoundResult.builder().collegeId(ctx.collegeId()).roundId(roundId).applicationId(a.getId()).build())));
        } else {
            DriveRound prev = rounds.get(idx - 1);
            if (prev.getStatus() != RoundStatus.COMPLETED) {
                throw new DomainException("Complete round " + prev.getSeq() + " (" + prev.getName() + ") first", HttpStatus.CONFLICT, "PREVIOUS_ROUND_OPEN");
            }
            var appIds = resultRepository.findAllByCollegeIdAndRoundId(ctx.collegeId(), roundId).stream().map(RoundResult::getApplicationId).toList();
            applicationRepository.findAllByIdInAndCollegeId(appIds, ctx.collegeId()).forEach(a -> participants.put(a.getId(), a));
        }
        var students = directory.students(ctx.collegeId(), participants.values().stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        CollegeCompany company = directory.company(ctx.collegeId(), drive.getCompanyId());
        for (CollegeApplication a : participants.values()) {
            a.setStatus(ApplicationStatus.IN_PROCESS);
            a.setCurrentRound(round.getSeq());
            applicationRepository.save(a);
            CollegeStudent s = students.get(a.getStudentId());
            if (s != null) {
                notificationService.notify(s.getUserId(), "ROUND_SHORTLIST", "You are shortlisted for " + round.getName() + " of " + company.getName() + ".");
                mailService.send(ctx.collegeId(), "SHORTLISTED", s.getEmail(), Map.of("studentName", CollegeDirectory.fullName(s),
                        "companyName", company.getName(), "roundName", round.getName()));
            }
        }
        round.setStatus(RoundStatus.ONGOING);
        roundRepository.save(round);
        if (drive.getStatus() == DriveStatus.PUBLISHED) {
            drive.setStatus(DriveStatus.ONGOING);
        }
        auditService.log(ctx, "ROUND_STARTED", "DRIVE", drive.getId(), "Started " + round.getName() + " with " + participants.size() + " student(s)", null);
        return toResponse(ctx.collegeId(), round);
    }

    @Transactional(readOnly = true)
    public List<RoundResultRow> results(CollegeContext ctx, UUID roundId) {
        directory.round(ctx.collegeId(), roundId);
        var results = resultRepository.findAllByCollegeIdAndRoundId(ctx.collegeId(), roundId);
        var apps = applicationRepository.findAllByIdInAndCollegeId(results.stream().map(RoundResult::getApplicationId).toList(), ctx.collegeId())
                .stream().collect(Collectors.toMap(CollegeApplication::getId, Function.identity()));
        var students = directory.students(ctx.collegeId(), apps.values().stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        var branches = directory.branches(ctx.collegeId());
        return results.stream().map(r -> {
            CollegeApplication a = apps.get(r.getApplicationId());
            CollegeStudent s = a == null ? null : students.get(a.getStudentId());
            if (s == null || !ctx.canAccessBranch(s.getBranchId())) return null;
            CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
            return new RoundResultRow(r.getId(), r.getApplicationId(), s.getId(), s.getRollNumber(), CollegeDirectory.fullName(s),
                    b == null ? null : b.getCode(), r.getResult(), r.getScore(), r.getRemarks());
        }).filter(Objects::nonNull).sorted(Comparator.comparing(RoundResultRow::rollNumber)).toList();
    }

    @Transactional
    public List<RoundResultRow> saveResults(CollegeContext ctx, UUID roundId, ResultsRequest req) {
        DriveRound round = requireOngoing(ctx, roundId);
        for (ResultEntry e : req.results()) {
            RoundResult r = resultRepository.findByRoundIdAndApplicationId(roundId, e.applicationId())
                    .filter(x -> x.getCollegeId().equals(ctx.collegeId()))
                    .orElseThrow(() -> new DomainException("Application " + e.applicationId() + " is not part of this round", HttpStatus.BAD_REQUEST, "NOT_IN_ROUND"));
            r.setResult(e.result());
            r.setScore(e.score());
            r.setRemarks(e.remarks());
            resultRepository.save(r);
        }
        auditService.log(ctx, "ROUND_RESULTS", "DRIVE", round.getDriveId(), "Recorded " + req.results().size() + " result(s) for " + round.getName(), null);
        return results(ctx, roundId);
    }

    /** Upload results as CSV / XLSX with columns roll_number, result (PASS/FAIL/ABSENT/HOLD), score, remarks. */
    @Transactional
    public RoundUploadResult upload(CollegeContext ctx, UUID roundId, MultipartFile file) {
        DriveRound round = requireOngoing(ctx, roundId);
        fileService.validate(file, CollegeFileService.SHEET_EXT);
        Map<String, RoundResultRow> byRoll = results(ctx, roundId).stream()
                .collect(Collectors.toMap(r -> r.rollNumber().toLowerCase(Locale.ROOT), Function.identity()));
        List<String> errors = new ArrayList<>();
        List<ResultEntry> entries = new ArrayList<>();
        List<String[]> rows = readRows(file);
        int line = 1;
        for (String[] row : rows) {
            line++;
            String roll = row[0] == null ? "" : row[0].trim().toLowerCase(Locale.ROOT);
            if (roll.isEmpty()) continue;
            RoundResultRow match = byRoll.get(roll);
            if (match == null) { errors.add("Row " + line + ": roll number " + row[0] + " is not in this round"); continue; }
            RoundResultValue v;
            try { v = RoundResultValue.valueOf(row[1] == null ? "" : row[1].trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { errors.add("Row " + line + ": result must be PASS, FAIL, ABSENT, HOLD or PENDING"); continue; }
            BigDecimal score = null;
            if (row[2] != null && !row[2].isBlank()) {
                try { score = new BigDecimal(row[2].trim()); } catch (NumberFormatException ex) { errors.add("Row " + line + ": invalid score"); continue; }
            }
            entries.add(new ResultEntry(match.applicationId(), v, score, row[3]));
        }
        if (!entries.isEmpty()) saveResults(ctx, roundId, new ResultsRequest(entries));
        auditService.log(ctx, "ROUND_RESULTS_UPLOADED", "DRIVE", round.getDriveId(), "Uploaded " + entries.size() + " result(s) for " + round.getName(),
                String.join("\n", errors));
        return new RoundUploadResult(entries.size(), errors);
    }

    @Transactional
    public RoundResponse complete(CollegeContext ctx, UUID roundId) {
        DriveRound round = requireOngoing(ctx, roundId);
        var results = resultRepository.findAllByCollegeIdAndRoundId(ctx.collegeId(), roundId);
        long open = results.stream().filter(r -> r.getResult() == RoundResultValue.PENDING || r.getResult() == RoundResultValue.HOLD).count();
        if (open > 0) {
            throw new DomainException(open + " student(s) still PENDING or on HOLD. Record a final result for everyone first.", HttpStatus.CONFLICT, "RESULTS_INCOMPLETE");
        }
        var rounds = roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(ctx.collegeId(), round.getDriveId());
        int idx = indexOf(rounds, round);
        DriveRound next = idx + 1 < rounds.size() ? rounds.get(idx + 1) : null;
        var apps = applicationRepository.findAllByIdInAndCollegeId(results.stream().map(RoundResult::getApplicationId).toList(), ctx.collegeId())
                .stream().collect(Collectors.toMap(CollegeApplication::getId, Function.identity()));
        var students = directory.students(ctx.collegeId(), apps.values().stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        int advanced = 0, selected = 0, rejected = 0;
        for (RoundResult r : results) {
            CollegeApplication a = apps.get(r.getApplicationId());
            if (a == null) continue;
            CollegeStudent s = students.get(a.getStudentId());
            if (r.getResult() == RoundResultValue.PASS) {
                if (next != null) {
                    resultRepository.findByRoundIdAndApplicationId(next.getId(), a.getId()).orElseGet(() -> resultRepository.save(
                            RoundResult.builder().collegeId(ctx.collegeId()).roundId(next.getId()).applicationId(a.getId()).build()));
                    advanced++;
                } else {
                    a.setStatus(ApplicationStatus.SELECTED);
                    selected++;
                    if (s != null) notificationService.notify(s.getUserId(), "SELECTED", "Congratulations! You cleared all rounds. Your offer will be recorded by the placement cell.");
                }
            } else {
                a.setStatus(ApplicationStatus.REJECTED);
                rejected++;
                if (s != null) notificationService.notify(s.getUserId(), "ROUND_RESULT", "Thank you for participating in " + round.getName() + ". You were not selected to continue.");
            }
            applicationRepository.save(a);
        }
        round.setStatus(RoundStatus.COMPLETED);
        roundRepository.save(round);
        auditService.log(ctx, "ROUND_COMPLETED", "DRIVE", round.getDriveId(), "Completed " + round.getName() + ": " + advanced + " advanced, "
                + selected + " selected, " + rejected + " rejected", null);
        return toResponse(ctx.collegeId(), round);
    }

    private DriveRound requireOngoing(CollegeContext ctx, UUID roundId) {
        DriveRound round = directory.round(ctx.collegeId(), roundId);
        if (round.getStatus() != RoundStatus.ONGOING) {
            throw new DomainException("Round is not in progress (status " + round.getStatus() + ")", HttpStatus.CONFLICT, "ROUND_NOT_ONGOING");
        }
        return round;
    }

    private static int indexOf(List<DriveRound> rounds, DriveRound round) {
        for (int i = 0; i < rounds.size(); i++) if (rounds.get(i).getId().equals(round.getId())) return i;
        return -1;
    }

    private RoundResponse toResponse(UUID collegeId, DriveRound r) {
        Map<String, Long> counts = new TreeMap<>(resultRepository.findAllByCollegeIdAndRoundId(collegeId, r.getId()).stream()
                .collect(Collectors.groupingBy(x -> x.getResult().name(), Collectors.counting())));
        return new RoundResponse(r.getId(), r.getDriveId(), r.getSeq(), r.getName(), r.getRoundType(), r.getScheduledAt(), r.getStatus(), counts);
    }

    private List<String[]> readRows(MultipartFile file) {
        List<String[]> out = new ArrayList<>();
        String ext = CollegeFileService.extensionOf(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            if (ext.equals(".csv")) {
                try (CSVParser p = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build()
                        .parse(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    for (CSVRecord rec : p) out.add(new String[]{get(rec, 0), get(rec, 1), get(rec, 2), get(rec, 3)});
                }
            } else {
                try (Workbook wb = WorkbookFactory.create(in)) {
                    Sheet sh = wb.getSheetAt(0);
                    DataFormatter f = new DataFormatter(Locale.ROOT);
                    for (int i = sh.getFirstRowNum() + 1; i <= sh.getLastRowNum(); i++) {
                        Row row = sh.getRow(i);
                        if (row == null) continue;
                        out.add(new String[]{f.formatCellValue(row.getCell(0)), f.formatCellValue(row.getCell(1)),
                                f.formatCellValue(row.getCell(2)), f.formatCellValue(row.getCell(3))});
                    }
                }
            }
        } catch (Exception e) {
            throw new DomainException("Could not read the results file", HttpStatus.BAD_REQUEST, "UPLOAD_UNREADABLE");
        }
        return out;
    }

    private static String get(CSVRecord r, int i) {
        return i < r.size() ? r.get(i) : null;
    }
}
