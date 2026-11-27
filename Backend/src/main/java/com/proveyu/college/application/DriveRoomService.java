package com.proveyu.college.application;

import com.proveyu.auth.application.AuthService;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.auth.web.dto.AuthDto.AuthResponse;
import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.application.dto.DriveRoomDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.config.AppProperties;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.security.PayloadDecryptionUtils;
import com.proveyu.shared.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Drive Room: company HR and panel members work inside one campus drive. Every change goes through the same
 * ApplicationService / RoundService / OfferService the TPO uses (with the company person as the audited actor), so
 * policies, auto-advance, PLACED status, notifications and reports stay identical in both portals.
 */
@Service
@RequiredArgsConstructor
public class DriveRoomService {

    /** What a company may set by hand; IN_PROCESS / SELECTED come from round results, exactly as for the TPO. */
    private static final Set<ApplicationStatus> COMPANY_STATUSES = EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.WAITLISTED, ApplicationStatus.REJECTED);
    private static final Set<DriveStatus> ACTIVE_DRIVE = EnumSet.of(DriveStatus.PUBLISHED, DriveStatus.ONGOING);

    private final DriveRecruiterRepository accessRepository;
    private final RoundScorecardRepository scorecardRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeOfferRepository offerRepository;
    private final RoundResultRepository resultRepository;
    private final StudentDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CollegeDirectory directory;
    private final ApplicationService applicationService;
    private final RoundService roundService;
    private final OfferService offerService;
    private final CollegeFileService fileService;
    private final TabularExporter exporter;
    private final AuditService auditService;
    private final AuthService authService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PayloadDecryptionUtils payloadDecryptionUtils;
    private final AppProperties appProperties;

    // ================================================================== TPO: manage company access

    @Transactional(readOnly = true)
    public List<RecruiterAccessResponse> list(CollegeContext ctx, UUID driveId) {
        directory.drive(ctx.collegeId(), driveId);
        return accessRepository.findAllByCollegeIdAndDriveIdOrderByCreatedAtAsc(ctx.collegeId(), driveId).stream().map(a -> toResponse(a, null)).toList();
    }

    @Transactional
    public RecruiterAccessResponse invite(CollegeContext ctx, UUID driveId, RecruiterInviteRequest r) {
        CollegeDrive drive = directory.drive(ctx.collegeId(), driveId);
        if (drive.getDeletedAt() != null) throw CollegeDirectory.notFound("Drive");
        String email = AuthService.normalizeEmail(r.email());
        userRepository.findByEmail(email).filter(u -> u.getRole() != Role.RECRUITER).ifPresent(u -> {
            throw new DomainException(email + " belongs to a non-recruiter account. Use the company's work e-mail.", HttpStatus.CONFLICT, "EMAIL_IN_USE");
        });
        DriveRecruiter a = accessRepository.findByDriveIdAndEmailIgnoreCase(driveId, email).orElse(null);
        if (a != null && a.getStatus() == DriveRecruiter.Status.ACTIVE) {
            throw new DomainException(email + " already has access to this drive", HttpStatus.CONFLICT, "ALREADY_ACTIVE");
        }
        if (a != null && a.getStatus() == DriveRecruiter.Status.INVITED) {
            throw new DomainException("An invitation is already pending for " + email + ". Use Resend.", HttpStatus.CONFLICT, "INVITE_PENDING");
        }
        if (a == null) a = DriveRecruiter.builder().collegeId(ctx.collegeId()).driveId(driveId).email(email).build();
        a.setFullName(r.fullName().trim());
        a.setDesignation(blankToNull(r.designation()));
        a.setShareDetails(r.shareDetails());
        a.setStatus(DriveRecruiter.Status.INVITED);
        a.setInvitedBy(ctx.userId());
        String raw = issueToken(a);
        a = accessRepository.save(a);
        sendInvite(ctx, drive, a, raw);
        auditService.log(ctx, "DRIVE_RECRUITER_INVITED", "DRIVE", driveId, "Invited " + email + " to " + drive.getTitle(),
                r.shareDetails() ? "Contact details and marks shared" : "Contact details hidden");
        return toResponse(a, raw);
    }

    @Transactional
    public RecruiterAccessResponse resend(CollegeContext ctx, UUID driveId, UUID accessId) {
        DriveRecruiter a = access(ctx, driveId, accessId);
        if (a.getStatus() != DriveRecruiter.Status.INVITED) throw new DomainException("Invitation is no longer pending", HttpStatus.CONFLICT, "INVITE_CLOSED");
        String raw = issueToken(a);
        accessRepository.save(a);
        sendInvite(ctx, directory.drive(ctx.collegeId(), driveId), a, raw);
        return toResponse(a, raw);
    }

    @Transactional
    public RecruiterAccessResponse update(CollegeContext ctx, UUID driveId, UUID accessId, RecruiterAccessUpdate r) {
        DriveRecruiter a = access(ctx, driveId, accessId);
        a.setShareDetails(r.shareDetails());
        accessRepository.save(a);
        auditService.log(ctx, "DRIVE_RECRUITER_UPDATED", "DRIVE", driveId, (r.shareDetails() ? "Shared" : "Hid") + " student details for " + a.getEmail(), null);
        return toResponse(a, null);
    }

    @Transactional
    public RecruiterAccessResponse revoke(CollegeContext ctx, UUID driveId, UUID accessId) {
        DriveRecruiter a = access(ctx, driveId, accessId);
        a.setStatus(DriveRecruiter.Status.REVOKED);
        a.setTokenHash(null);
        accessRepository.save(a);
        auditService.log(ctx, "DRIVE_RECRUITER_REVOKED", "DRIVE", driveId, "Revoked drive access for " + a.getEmail(), null);
        return toResponse(a, null);
    }

    /** Panel scorecards of a round, for the TPO's view. */
    @Transactional(readOnly = true)
    public List<ScorecardResponse> scorecardsForCollege(CollegeContext ctx, UUID roundId) {
        directory.round(ctx.collegeId(), roundId);
        return scorecardRepository.findAllByCollegeIdAndRoundIdOrderByUpdatedAtDesc(ctx.collegeId(), roundId).stream()
                .map(s -> toScorecard(s, null)).toList();
    }

    // ================================================================== invitation (public)

    @Transactional(readOnly = true)
    public RecruiterInviteInfo inviteInfo(String rawToken) {
        DriveRecruiter a = validInvite(rawToken);
        CollegeDrive d = directory.drive(a.getCollegeId(), a.getDriveId());
        return new RecruiterInviteInfo(directory.profile(a.getCollegeId()).getCollegeName(), d.getTitle(),
                directory.company(a.getCollegeId(), d.getCompanyId()).getName(), a.getEmail(), a.getFullName(),
                userRepository.findByEmail(a.getEmail()).isPresent(), a.getExpiresAt());
    }

    /** New e-mail: creates a RECRUITER account. Existing recruiter: must confirm with their current password. */
    @Transactional
    public AuthResponse accept(RecruiterAcceptRequest r) {
        DriveRecruiter a = validInvite(r.token());
        String password = payloadDecryptionUtils.decryptPassword(r.password());
        User user = userRepository.findByEmail(a.getEmail()).orElse(null);
        if (user != null) {
            if (user.getRole() != Role.RECRUITER) throw new DomainException("This e-mail belongs to a non-recruiter account", HttpStatus.CONFLICT, "EMAIL_IN_USE");
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new DomainException("Wrong password for your existing ProveYu account", HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");
            }
            if (user.getEmailVerifiedAt() == null) user.setEmailVerifiedAt(Instant.now()); // the invitation link proves the mailbox
            user = userRepository.save(user);
        } else {
            com.proveyu.shared.security.PasswordRules.require(password);
            user = userRepository.save(User.builder().email(a.getEmail()).role(Role.RECRUITER).active(true).emailVerifiedAt(Instant.now())
                    .fullName(r.fullName() == null || r.fullName().isBlank() ? a.getFullName() : r.fullName().trim())
                    .passwordHash(passwordEncoder.encode(password)).build());
        }
        a.setUserId(user.getId());
        a.setStatus(DriveRecruiter.Status.ACTIVE);
        a.setAcceptedAt(Instant.now());
        a.setTokenHash(null);
        accessRepository.save(a);
        auditService.log(a.getCollegeId(), user.getId(), user.getFullName(), "DRIVE_RECRUITER_JOINED", "DRIVE", a.getDriveId(),
                user.getFullName() + " (" + a.getEmail() + ") joined the drive room", null);
        return authService.issueSession(user);
    }

    // ================================================================== company: read

    @Transactional(readOnly = true)
    public List<RoomDriveCard> myDrives(UUID userId) {
        var access = accessRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, DriveRecruiter.Status.ACTIVE);
        List<RoomDriveCard> cards = access.stream()
                .map(a -> {
                    CollegeDrive d = directory.drive(a.getCollegeId(), a.getDriveId());
                    if (d.getDeletedAt() != null) return null;
                    if (directory.profile(a.getCollegeId()).getApprovalStatus() == com.proveyu.college.domain.CollegeEnums.ApprovalStatus.SUSPENDED) return null;
                    var apps = applicationRepository.findAllByCollegeIdAndDriveId(a.getCollegeId(), d.getId());
                    long offers = offerRepository.findAllByCollegeIdAndDriveId(a.getCollegeId(), d.getId()).stream()
                            .filter(o -> o.getStatus() != OfferStatus.REVOKED).count();
                    return new RoomDriveCard(d.getId(), directory.profile(a.getCollegeId()).getCollegeName(),
                            directory.company(a.getCollegeId(), d.getCompanyId()).getName(), d.getTitle(), d.getRoleTitle(), d.getStatus(),
                            d.getDriveDate(), d.getCtcLpa(), apps.stream().filter(x -> x.getStatus() != ApplicationStatus.WITHDRAWN).count(),
                            apps.stream().filter(x -> x.getStatus() != ApplicationStatus.APPLIED && x.getStatus() != ApplicationStatus.WITHDRAWN
                                    && x.getStatus() != ApplicationStatus.REJECTED).count(),
                            apps.stream().filter(x -> x.getStatus() == ApplicationStatus.SELECTED).count(), offers);
                }).filter(Objects::nonNull).toList();
        // nothing to show only because of a suspension: say so, instead of "no drives yet"
        if (cards.isEmpty()) access.forEach(a -> directory.requireNotSuspended(a.getCollegeId()));
        return cards;
    }

    @Transactional
    public RoomSummary summary(UUID userId, UUID driveId) {
        Room room = room(userId, driveId);
        CollegeDrive d = room.drive;
        var apps = applicationRepository.findAllByCollegeIdAndDriveId(room.collegeId(), d.getId());
        Map<String, Long> funnel = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) funnel.put(s.name(), apps.stream().filter(a -> a.getStatus() == s).count());
        funnel.put("OFFERED", offerRepository.findAllByCollegeIdAndDriveId(room.collegeId(), d.getId()).stream()
                .filter(o -> o.getStatus() != OfferStatus.REVOKED).count());
        List<RoomRound> rounds = roundService.list(room.ctx, d.getId()).stream()
                .map(r -> new RoomRound(r.id(), r.seq(), r.name(), r.roundType(), r.scheduledAt(), r.status(), r.resultCounts())).toList();
        return new RoomSummary(d.getId(), directory.profile(room.collegeId()).getCollegeName(), room.company.getName(), room.company.getId(),
                d.getTitle(), d.getRoleTitle(), d.getDriveType(), d.getStatus(), d.getCtcLpa(), d.getStipendMonthly(), d.getLocation(), d.getMode(),
                d.getVenue(), d.getDriveDate(), d.getRegistrationDeadline(), d.getJobDescription(), room.access.isShareDetails(),
                ACTIVE_DRIVE.contains(d.getStatus()), funnel, rounds, room.user.getFullName());
    }

    @Transactional
    public List<RoomApplicant> applicants(UUID userId, UUID driveId) {
        Room room = room(userId, driveId);
        UUID collegeId = room.collegeId();
        var apps = applicationRepository.findAllByCollegeIdAndDriveId(collegeId, driveId).stream()
                .filter(a -> a.getStatus() != ApplicationStatus.WITHDRAWN)
                .sorted(Comparator.comparing(CollegeApplication::getCreatedAt)).toList();
        var students = directory.students(collegeId, apps.stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        var branches = directory.branches(collegeId);
        Map<UUID, String> roundNames = roundService.list(room.ctx, driveId).stream().collect(Collectors.toMap(RoundResponse::id, RoundResponse::name));
        Map<UUID, Map<String, String>> results = new HashMap<>();
        if (!apps.isEmpty()) {
            resultRepository.findAllByCollegeIdAndApplicationIdIn(collegeId, apps.stream().map(CollegeApplication::getId).toList()).stream()
                    .filter(r -> roundNames.containsKey(r.getRoundId()))
                    .forEach(r -> results.computeIfAbsent(r.getApplicationId(), k -> new LinkedHashMap<>()).put(r.getRoundId().toString(), r.getResult().name()));
        }
        Map<UUID, CollegeOffer> offers = offerRepository.findAllByCollegeIdAndDriveId(collegeId, driveId).stream()
                .filter(o -> o.getStatus() != OfferStatus.REVOKED).collect(Collectors.toMap(CollegeOffer::getStudentId, Function.identity(), (x, y) -> y));
        boolean share = room.access.isShareDetails();
        return apps.stream().map(a -> {
            CollegeStudent s = students.get(a.getStudentId());
            if (s == null) return null;
            CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
            CollegeOffer o = offers.get(s.getId());
            return new RoomApplicant(a.getId(), s.getId(), CollegeDirectory.fullName(s), s.getRollNumber(), b == null ? null : b.getName(),
                    b == null ? null : b.getCode(), s.getBatchYear(), s.getDegree(), s.getCgpa(), s.getActiveBacklogs(), StudentService.splitSkills(s.getSkills()),
                    s.getLinkedinUrl(), s.getGithubUrl(), share ? s.getEmail() : null, share ? s.getPhone() : null,
                    share ? s.getTenthPercent() : null, share ? s.getTwelfthPercent() : null, a.getStatus(), a.getCurrentRound(),
                    resume(collegeId, s.getId()) != null, results.getOrDefault(a.getId(), Map.of()), o == null ? null : o.getStatus(),
                    o == null ? null : o.getId(), a.getCreatedAt());
        }).filter(Objects::nonNull).toList();
    }

    @Transactional
    public CollegeFile resumeFile(UUID userId, UUID driveId, UUID applicationId) {
        Room room = room(userId, driveId);
        CollegeApplication a = ownApplication(room, applicationId);
        StudentDocument d = resume(room.collegeId(), a.getStudentId());
        if (d == null) throw CollegeDirectory.notFound("Resume");
        return fileService.get(room.collegeId(), d.getFileId());
    }

    /** All available resumes of the given (or all non-withdrawn) applicants as one ZIP. */
    @Transactional
    public byte[] resumesZip(UUID userId, UUID driveId, List<UUID> applicationIds) {
        Room room = room(userId, driveId);
        var apps = applicationRepository.findAllByCollegeIdAndDriveId(room.collegeId(), driveId).stream()
                .filter(a -> a.getStatus() != ApplicationStatus.WITHDRAWN)
                .filter(a -> applicationIds == null || applicationIds.isEmpty() || applicationIds.contains(a.getId())).toList();
        var students = directory.students(room.collegeId(), apps.stream().map(CollegeApplication::getStudentId).collect(Collectors.toSet()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int added = 0;
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (CollegeApplication a : apps) {
                CollegeStudent s = students.get(a.getStudentId());
                StudentDocument d = s == null ? null : resume(room.collegeId(), s.getId());
                if (d == null) continue;
                CollegeFile f = fileService.get(room.collegeId(), d.getFileId());
                String ext = CollegeFileService.extensionOf(f.getOriginalName());
                zip.putNextEntry(new ZipEntry(safe(s.getRollNumber() + "_" + CollegeDirectory.fullName(s)) + (ext.isEmpty() ? ".pdf" : ext)));
                try (InputStream in = fileService.load(f).getInputStream()) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
                added++;
            }
        } catch (IOException e) {
            throw new DomainException("Could not build the resume ZIP", HttpStatus.INTERNAL_SERVER_ERROR, "ZIP_FAILED");
        }
        if (added == 0) throw new DomainException("None of these students has uploaded a resume yet", HttpStatus.NOT_FOUND, "NO_RESUMES");
        auditService.log(room.ctx, "DRIVE_RESUMES_DOWNLOADED", "DRIVE", driveId, added + " resume(s) downloaded by " + room.ctx.actorName(), null);
        return out.toByteArray();
    }

    // ================================================================== company: actions (same services as the TPO)

    public BulkResult setStatus(UUID userId, UUID driveId, RoomStatusRequest r) {
        Room room = room(userId, driveId);
        requireActive(room);
        if (!COMPANY_STATUSES.contains(r.status())) {
            throw new DomainException("Companies can shortlist, waitlist or reject. Round results set the other statuses.", HttpStatus.BAD_REQUEST, "STATUS_NOT_ALLOWED");
        }
        r.applicationIds().forEach(id -> ownApplication(room, id));
        return applicationService.bulkStatus(room.ctx, new BulkStatusRequest(r.applicationIds(), r.status(), r.remarks()));
    }

    public RoundResponse startRound(UUID userId, UUID driveId, UUID roundId) {
        Room room = room(userId, driveId);
        requireActive(room);
        ownRound(room, roundId);
        return roundService.start(room.ctx, roundId);
    }

    public RoundResponse completeRound(UUID userId, UUID driveId, UUID roundId) {
        Room room = room(userId, driveId);
        requireActive(room);
        ownRound(room, roundId);
        return roundService.complete(room.ctx, roundId);
    }

    public List<RoundResultRow> roundResults(UUID userId, UUID driveId, UUID roundId) {
        Room room = room(userId, driveId);
        ownRound(room, roundId);
        return roundService.results(room.ctx, roundId);
    }

    public List<RoundResultRow> saveResults(UUID userId, UUID driveId, UUID roundId, ResultsRequest r) {
        Room room = room(userId, driveId);
        requireActive(room);
        ownRound(room, roundId);
        return roundService.saveResults(room.ctx, roundId, r);
    }

    @Transactional(readOnly = true)
    public List<ScorecardResponse> scorecards(UUID userId, UUID driveId, UUID roundId) {
        Room room = room(userId, driveId);
        ownRound(room, roundId);
        return scorecardRepository.findAllByCollegeIdAndRoundIdOrderByUpdatedAtDesc(room.collegeId(), roundId).stream()
                .map(s -> toScorecard(s, userId)).toList();
    }

    /** Saves this interviewer's scorecard and writes the shared round result (latest scorecard wins). */
    @Transactional
    public ScorecardResponse saveScorecard(UUID userId, UUID driveId, UUID roundId, UUID applicationId, ScorecardRequest r) {
        Room room = room(userId, driveId);
        requireActive(room);
        ownRound(room, roundId);
        ownApplication(room, applicationId);
        if (r.result() == RoundResultValue.PENDING) throw new DomainException("Choose Pass, Fail, Hold or Absent", HttpStatus.BAD_REQUEST, "RESULT_REQUIRED");
        RoundScorecard s = scorecardRepository.findByRoundIdAndApplicationIdAndUserId(roundId, applicationId, userId)
                .orElse(RoundScorecard.builder().collegeId(room.collegeId()).roundId(roundId).applicationId(applicationId).userId(userId).build());
        s.setInterviewerName(room.user.getFullName());
        s.setTechnical(r.technical());
        s.setCommunication(r.communication());
        s.setAttitude(r.attitude());
        s.setResult(r.result());
        s.setRemarks(blankToNull(r.remarks()));
        s = scorecardRepository.save(s);
        List<Integer> ratings = new ArrayList<>();
        for (Integer x : Arrays.asList(r.technical(), r.communication(), r.attitude())) if (x != null) ratings.add(x);
        BigDecimal score = ratings.isEmpty() ? null
                : BigDecimal.valueOf(ratings.stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP);
        String summary = ("Panel " + room.user.getFullName() + ": tech " + dash(r.technical()) + "/5, comm " + dash(r.communication()) + "/5, attitude "
                + dash(r.attitude()) + "/5" + (s.getRemarks() == null ? "" : " - " + s.getRemarks()));
        roundService.saveResults(room.ctx, roundId, new ResultsRequest(List.of(new ResultEntry(applicationId, r.result(), score,
                summary.length() > 500 ? summary.substring(0, 497) + "..." : summary))));
        return toScorecard(s, userId);
    }

    public List<OfferResponse> offers(UUID userId, UUID driveId) {
        Room room = room(userId, driveId);
        return offerService.all(room.ctx, null, null, driveId, null, null);
    }

    /**
     * Records offers for SELECTED applicants through OfferService, one transaction per student, so a placement-policy
     * refusal for one student does not undo the others.
     */
    public RoomOfferResult createOffers(UUID userId, UUID driveId, RoomOfferRequest r) {
        Room room = room(userId, driveId);
        requireActive(room);
        BigDecimal ctc = r.ctcLpa() != null ? r.ctcLpa() : room.drive.getCtcLpa();
        if (ctc == null) throw new DomainException("Enter the CTC for these offers", HttpStatus.BAD_REQUEST, "CTC_REQUIRED");
        String role = r.roleTitle() == null || r.roleTitle().isBlank() ? room.drive.getRoleTitle() : r.roleTitle().trim();
        int created = 0;
        List<String> errors = new ArrayList<>();
        for (UUID appId : r.applicationIds()) {
            CollegeApplication a = ownApplication(room, appId);
            CollegeStudent s = directory.student(room.collegeId(), a.getStudentId());
            String who = s.getRollNumber() + " " + CollegeDirectory.fullName(s);
            if (a.getStatus() != ApplicationStatus.SELECTED) { errors.add(who + ": not selected yet"); continue; }
            try {
                offerService.create(room.ctx, new OfferRequest(s.getId(), driveId, room.company.getId(), role, ctc, r.ctcBreakup(),
                        r.offerDate(), r.joiningDate(), "Recorded by " + room.ctx.actorName(), false, null));
                created++;
            } catch (DomainException e) {
                errors.add(who + ": " + e.getMessage());
            }
        }
        return new RoomOfferResult(created, errors);
    }

    public OfferResponse uploadLetter(UUID userId, UUID driveId, UUID offerId, MultipartFile file) {
        Room room = room(userId, driveId);
        CollegeOffer o = directory.offer(room.collegeId(), offerId);
        if (!driveId.equals(o.getDriveId())) throw CollegeDirectory.notFound("Offer");
        return offerService.uploadLetter(room.ctx, offerId, file);
    }

    /** Drive report for the company: every applicant with round results and offer. */
    @Transactional
    public TabularExporter.Export report(UUID userId, UUID driveId, String format) {
        RoomSummary sum = summary(userId, driveId);
        List<RoomApplicant> apps = applicants(userId, driveId);
        List<String> headers = new ArrayList<>(List.of("Roll number", "Name", "Branch", "Batch", "CGPA", "Status"));
        sum.rounds().forEach(r -> headers.add("R" + r.seq() + " " + r.name()));
        headers.addAll(List.of("Offer", "Resume"));
        if (sum.shareDetails()) headers.addAll(List.of("E-mail", "Phone"));
        List<List<Object>> rows = new ArrayList<>();
        for (RoomApplicant a : apps) {
            List<Object> row = new ArrayList<>(List.of(a.rollNumber(), a.name(), Objects.toString(a.branchCode(), "-"), Objects.toString(a.batchYear(), "-"),
                    a.cgpa() == null ? "-" : a.cgpa(), a.status().name()));
            sum.rounds().forEach(r -> row.add(a.roundResults().getOrDefault(r.id().toString(), "-")));
            row.add(a.offerStatus() == null ? "-" : a.offerStatus().name());
            row.add(a.hasResume() ? "Yes" : "No");
            if (sum.shareDetails()) { row.add(Objects.toString(a.email(), "-")); row.add(Objects.toString(a.phone(), "-")); }
            rows.add(row);
        }
        return exporter.export(new TabularExporter.Table(sum.companyName() + " - " + sum.title() + " (" + sum.collegeName() + ")", headers, rows),
                format, "drive-report-" + safe(sum.title()));
    }

    // ================================================================== helpers

    private record Room(DriveRecruiter access, CollegeDrive drive, CollegeCompany company, User user, CollegeContext ctx) {
        UUID collegeId() { return access.getCollegeId(); }
    }

    /** Resolves the caller's ACTIVE access to this drive; anything else is a plain 404 (no hint that the drive exists). */
    private Room room(UUID userId, UUID driveId) {
        DriveRecruiter a = accessRepository.findByDriveIdAndUserIdAndStatus(driveId, userId, DriveRecruiter.Status.ACTIVE)
                .orElseThrow(() -> CollegeDirectory.notFound("Drive"));
        CollegeDrive d = directory.drive(a.getCollegeId(), driveId);
        if (d.getDeletedAt() != null) throw CollegeDirectory.notFound("Drive");
        directory.requireNotSuspended(a.getCollegeId());
        CollegeCompany c = directory.company(a.getCollegeId(), d.getCompanyId());
        User u = userRepository.findById(userId).orElseThrow(() -> CollegeDirectory.notFound("User"));
        if (a.getLastAccessAt() == null || a.getLastAccessAt().isBefore(Instant.now().minus(Duration.ofMinutes(5)))) {
            a.setLastAccessAt(Instant.now());
            accessRepository.save(a);
        }
        // the company person acts through the college services; branch scope empty = the whole drive
        CollegeContext ctx = new CollegeContext(a.getCollegeId(), userId, null, MemberRole.COLLEGE_COORDINATOR, Set.of(),
                u.getFullName() + " (" + c.getName() + ")");
        return new Room(a, d, c, u, ctx);
    }

    private static void requireActive(Room room) {
        if (!ACTIVE_DRIVE.contains(room.drive.getStatus())) {
            throw new DomainException("This drive is " + room.drive.getStatus().name().toLowerCase(Locale.ROOT) + "; changes are closed", HttpStatus.CONFLICT, "DRIVE_CLOSED");
        }
    }

    private CollegeApplication ownApplication(Room room, UUID applicationId) {
        CollegeApplication a = directory.application(room.collegeId(), applicationId);
        if (!a.getDriveId().equals(room.drive.getId())) throw CollegeDirectory.notFound("Application");
        return a;
    }

    private DriveRound ownRound(Room room, UUID roundId) {
        DriveRound r = directory.round(room.collegeId(), roundId);
        if (!r.getDriveId().equals(room.drive.getId())) throw CollegeDirectory.notFound("Round");
        return r;
    }

    private StudentDocument resume(UUID collegeId, UUID studentId) {
        return documentRepository.findAllByCollegeIdAndStudentIdOrderByCreatedAtDesc(collegeId, studentId).stream()
                .filter(d -> d.getDocType() == DocType.RESUME && d.getVerificationStatus() != VerificationStatus.REJECTED)
                .findFirst().orElse(null);
    }

    private DriveRecruiter access(CollegeContext ctx, UUID driveId, UUID accessId) {
        DriveRecruiter a = accessRepository.findByIdAndCollegeId(accessId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Access"));
        if (!a.getDriveId().equals(driveId)) throw CollegeDirectory.notFound("Access");
        return a;
    }

    private DriveRecruiter validInvite(String rawToken) {
        DriveRecruiter a = accessRepository.findByTokenHash(TokenUtils.sha256(rawToken == null ? "" : rawToken))
                .orElseThrow(() -> new DomainException("Invalid or already used invitation link", HttpStatus.NOT_FOUND, "INVITE_INVALID"));
        if (a.getStatus() != DriveRecruiter.Status.INVITED) throw new DomainException("This invitation is no longer valid", HttpStatus.GONE, "INVITE_CLOSED");
        if (a.getExpiresAt() == null || a.getExpiresAt().isBefore(Instant.now())) throw new DomainException("This invitation has expired. Ask the placement cell to resend it.", HttpStatus.GONE, "INVITE_EXPIRED");
        return a;
    }

    private static String issueToken(DriveRecruiter a) {
        String raw = TokenUtils.randomUrlToken();
        a.setTokenHash(TokenUtils.sha256(raw));
        a.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return raw;
    }

    private void sendInvite(CollegeContext ctx, CollegeDrive drive, DriveRecruiter a, String raw) {
        String college = directory.profile(ctx.collegeId()).getCollegeName();
        String link = appProperties.getFrontendUrl() + "/company/accept?token=" + raw;
        emailService.sendHtml(a.getEmail(), "Your Drive Room for " + drive.getTitle() + " at " + college,
                "<p>Hi " + HtmlUtils.htmlEscape(a.getFullName()) + ",</p><p>" + HtmlUtils.htmlEscape(ctx.actorName()) + " from <b>" + HtmlUtils.htmlEscape(college)
                        + "</b> has given you access to the campus drive <b>" + HtmlUtils.htmlEscape(drive.getTitle())
                        + "</b>. You can see applicants, download resumes, shortlist, record round results and offers.</p>"
                        + "<p><a href=\"" + link + "\">Open your Drive Room</a> (link valid for 7 days)</p>");
    }

    private RecruiterAccessResponse toResponse(DriveRecruiter a, String rawToken) {
        return new RecruiterAccessResponse(a.getId(), a.getDriveId(), a.getEmail(), a.getFullName(), a.getDesignation(), a.getStatus(), a.isShareDetails(),
                a.getExpiresAt(), a.getAcceptedAt(), a.getLastAccessAt(), a.getCreatedAt(), rawToken != null && appProperties.isExposeTokens() ? rawToken : null);
    }

    private static ScorecardResponse toScorecard(RoundScorecard s, UUID me) {
        return new ScorecardResponse(s.getId(), s.getRoundId(), s.getApplicationId(), s.getUserId(), s.getInterviewerName(), s.getTechnical(),
                s.getCommunication(), s.getAttitude(), s.getResult(), s.getRemarks(), s.getUpdatedAt(), s.getUserId().equals(me));
    }

    private static String dash(Integer v) {
        return v == null ? "-" : v.toString();
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
