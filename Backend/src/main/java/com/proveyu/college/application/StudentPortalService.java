package com.proveyu.college.application;

import com.proveyu.auth.application.AuthService;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.auth.web.dto.AuthDto.ForgotPasswordRequest;
import com.proveyu.auth.web.dto.AuthDto.ForgotPasswordResponse;
import com.proveyu.college.application.dto.DriveDtos.OfferResponse;
import com.proveyu.college.application.dto.StudentDtos.DocumentResponse;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContextResolver;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.web.PageResponse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Self-service "Campus Drives" portal for students (CANDIDATE accounts linked to a college student record). */
@Service
@RequiredArgsConstructor
public class StudentPortalService {

    public record PortalProfile(UUID studentId, UUID userId, String collegeName, String rollNumber, String name, String email, String phone, String branch,
                                Integer batchYear, String degree, BigDecimal cgpa, BigDecimal tenthPercent, BigDecimal twelfthPercent, int activeBacklogs,
                                List<String> skills, String linkedinUrl, String githubUrl, StudentStatus status, BigDecimal highestCtcLpa,
                                PolicyMode placementPolicy, long unreadAnnouncements) {}

    public record ProfileUpdate(@Size(max = 15) @Pattern(regexp = "^$|^[+0-9 ()-]{7,15}$", message = "Enter a valid phone number") String phone,
                                @Size(max = 40) List<@Size(max = 60) String> skills,
                                @Size(max = 300) @Pattern(regexp = "^$|^https?://\\S+$", message = "Must start with http:// or https://") String linkedinUrl,
                                @Size(max = 300) @Pattern(regexp = "^$|^https?://\\S+$", message = "Must start with http:// or https://") String githubUrl) {}

    public record PortalRound(UUID roundId, int seq, String name, RoundType roundType, RoundStatus status, Instant scheduledAt, RoundResultValue result) {}

    public record PortalInterview(UUID id, UUID applicationId, String companyName, String driveTitle, String roundName, Instant startsAt, Instant endsAt,
                                  String room, String meetingLink, String panelName, SlotStatus status) {}

    public record PortalAnnouncement(UUID id, String title, String body, String fromName, Instant createdAt, boolean read) {}

    public record PortalContact(UUID userId, String name, String type, String subtitle) {}

    public record PortalDrive(UUID id, String title, String companyName, String roleTitle, DriveType driveType, BigDecimal ctcLpa,
                              BigDecimal stipendMonthly, String location, DriveMode mode, String venue, Instant registrationDeadline, LocalDate driveDate,
                              DriveStatus status, String jobDescription, List<String> skills, int bondMonths, String tierName, boolean eligible,
                              List<String> reasons, UUID applicationId, ApplicationStatus applicationStatus, boolean canApply) {}

    public record PortalApplication(UUID id, UUID driveId, String driveTitle, String companyName, ApplicationStatus status, int currentRound, Instant appliedAt) {}

    private final CollegeStudentRepository studentRepository;
    private final CollegeDriveRepository driveRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeOfferRepository offerRepository;
    private final CollegeDirectory directory;
    private final EligibilityService eligibilityService;
    private final ApplicationService applicationService;
    private final OfferService offerService;
    private final StudentService studentService;
    private final DriveRoundRepository roundRepository;
    private final RoundResultRepository roundResultRepository;
    private final InterviewSlotRepository slotRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;
    private final StudentDocumentRepository documentRepository;
    private final CollegeMemberRepository memberRepository;
    private final CollegeFileService fileService;
    private final UserRepository userRepository;
    private final StudentAccountService accountService;
    private final AuthService authService;

    public CollegeStudent me(UUID userId) {
        return studentRepository.findFirstByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new DomainException("Your account is not linked to a college. Ask your placement cell to add you.", HttpStatus.NOT_FOUND, "NOT_A_COLLEGE_STUDENT"));
    }

    @Transactional(readOnly = true)
    public PortalProfile profile(UUID userId) {
        CollegeStudent s = me(userId);
        CollegeBranch b = s.getBranchId() == null ? null : directory.branches(s.getCollegeId()).get(s.getBranchId());
        CollegeProfile college = directory.profile(s.getCollegeId());
        return new PortalProfile(s.getId(), userId, college.getCollegeName(), s.getRollNumber(), CollegeDirectory.fullName(s), s.getEmail(), s.getPhone(),
                b == null ? null : b.getName(), s.getBatchYear(), s.getDegree(), s.getCgpa(), s.getTenthPercent(), s.getTwelfthPercent(), s.getActiveBacklogs(),
                StudentService.splitSkills(s.getSkills()), s.getLinkedinUrl(), s.getGithubUrl(), s.getStatus(), s.getHighestCtcLpa(),
                college.getPolicyMode(), recipientRepository.countByCollegeIdAndStudentIdAndReadAtIsNull(s.getCollegeId(), s.getId()));
    }

    /** Students may edit contact details and skills only; academic data is owned by the placement cell. */
    @Transactional
    public PortalProfile updateProfile(UUID userId, ProfileUpdate r) {
        CollegeStudent s = me(userId);
        s.setPhone(blankToNull(r.phone()));
        if (r.skills() != null) {
            s.setSkills(r.skills().stream().map(String::trim).filter(x -> !x.isEmpty()).distinct().collect(Collectors.joining(", ")));
        }
        s.setLinkedinUrl(blankToNull(r.linkedinUrl()));
        s.setGithubUrl(blankToNull(r.githubUrl()));
        studentRepository.save(s);
        return profile(userId);
    }

    @Transactional(readOnly = true)
    public List<PortalDrive> drives(UUID userId) {
        CollegeStudent s = me(userId);
        Map<UUID, CollegeApplication> apps = applicationRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream()
                .collect(Collectors.toMap(CollegeApplication::getDriveId, a -> a));
        var drives = driveRepository.findAllByCollegeIdAndDeletedAtIsNull(s.getCollegeId()).stream()
                .filter(d -> d.getStatus() == DriveStatus.PUBLISHED || d.getStatus() == DriveStatus.ONGOING || apps.containsKey(d.getId()))
                .filter(d -> d.getStatus() != DriveStatus.DRAFT).toList();
        var companies = directory.companies(s.getCollegeId(), drives.stream().map(CollegeDrive::getCompanyId).collect(Collectors.toSet()));
        var tiers = directory.tiers(s.getCollegeId());
        Instant now = Instant.now();
        return drives.stream().map(d -> {
            var r = eligibilityService.evaluate(s.getCollegeId(), d, s);
            CollegeApplication a = apps.get(d.getId());
            boolean open = d.getStatus() == DriveStatus.PUBLISHED && (d.getRegistrationDeadline() == null || d.getRegistrationDeadline().isAfter(now));
            boolean canApply = open && r.eligible() && (a == null || a.getStatus() == ApplicationStatus.WITHDRAWN);
            Integer rank = EligibilityEngine.driveTierRank(d, tiers);
            String tierName = rank == null ? null : tiers.stream().filter(t -> t.getTierRank().equals(rank)).map(CollegeTier::getName).findFirst().orElse(null);
            return new PortalDrive(d.getId(), d.getTitle(), Optional.ofNullable(companies.get(d.getCompanyId())).map(CollegeCompany::getName).orElse("-"),
                    d.getRoleTitle(), d.getDriveType(), d.getCtcLpa(), d.getStipendMonthly(), d.getLocation(), d.getMode(), d.getVenue(),
                    d.getRegistrationDeadline(), d.getDriveDate(), d.getStatus(), d.getJobDescription(), StudentService.splitSkills(d.getSkills()),
                    d.getBondMonths(), tierName, r.eligible(), r.reasons(), a == null ? null : a.getId(), a == null ? null : a.getStatus(), canApply);
        }).sorted(Comparator.comparing((PortalDrive p) -> !p.canApply()).thenComparing(PortalDrive::registrationDeadline, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public PortalApplication apply(UUID userId, UUID driveId) {
        CollegeStudent s = me(userId);
        return toApp(s.getCollegeId(), applicationService.apply(s, driveId));
    }

    @Transactional
    public PortalApplication withdraw(UUID userId, UUID applicationId) {
        CollegeStudent s = me(userId);
        return toApp(s.getCollegeId(), applicationService.withdraw(s, applicationId));
    }

    @Transactional(readOnly = true)
    public List<PortalApplication> applications(UUID userId) {
        CollegeStudent s = me(userId);
        return applicationRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream()
                .sorted(Comparator.comparing(CollegeApplication::getCreatedAt).reversed()).map(a -> toApp(s.getCollegeId(), a)).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> offers(UUID userId) {
        CollegeStudent s = me(userId);
        return offerRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream().map(o -> offerService.one(s.getCollegeId(), o)).toList();
    }

    @Transactional
    public OfferResponse respond(UUID userId, UUID offerId, boolean accept) {
        CollegeStudent s = me(userId);
        CollegeOffer o = directory.offer(s.getCollegeId(), offerId);
        if (!o.getStudentId().equals(s.getId())) throw CollegeDirectory.notFound("Offer");
        if (o.getStatus() != OfferStatus.OFFERED) throw new DomainException("This offer can no longer be changed", HttpStatus.CONFLICT, "OFFER_CLOSED");
        return offerService.transition(s.getCollegeId(), userId, CollegeDirectory.fullName(s), o, s, accept ? OfferStatus.ACCEPTED : OfferStatus.DECLINED,
                accept ? "Accepted by student" : "Declined by student");
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(UUID userId) {
        CollegeStudent s = me(userId);
        return studentService.documents(s.getCollegeId(), s.getId());
    }

    @Transactional
    public DocumentResponse upload(UUID userId, DocType type, String label, MultipartFile file) {
        CollegeStudent s = me(userId);
        return studentService.uploadDocument(s.getCollegeId(), userId, s.getId(), type, label, file);
    }

    @Transactional(readOnly = true)
    public CollegeFile documentFile(UUID userId, UUID docId) {
        CollegeStudent s = me(userId);
        return fileService.get(s.getCollegeId(), ownDocument(s, docId).getFileId());
    }

    /** A verified document is part of the college's record, so only the placement cell can remove it. */
    @Transactional
    public void deleteDocument(UUID userId, UUID docId) {
        CollegeStudent s = me(userId);
        StudentDocument d = ownDocument(s, docId);
        if (d.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new DomainException("Verified documents can only be removed by your placement cell", HttpStatus.CONFLICT, "DOCUMENT_VERIFIED");
        }
        documentRepository.delete(d);
    }

    @Transactional(readOnly = true)
    public CollegeFile offerLetter(UUID userId, UUID offerId) {
        CollegeStudent s = me(userId);
        CollegeOffer o = directory.offer(s.getCollegeId(), offerId);
        if (!o.getStudentId().equals(s.getId())) throw CollegeDirectory.notFound("Offer");
        return offerService.letter(s.getCollegeId(), o);
    }

    /** Round-by-round progress of one of the student's applications. */
    @Transactional(readOnly = true)
    public List<PortalRound> rounds(UUID userId, UUID applicationId) {
        CollegeStudent s = me(userId);
        CollegeApplication a = applicationRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream()
                .filter(x -> x.getId().equals(applicationId)).findFirst().orElseThrow(() -> CollegeDirectory.notFound("Application"));
        Map<UUID, RoundResultValue> results = roundResultRepository.findAllByCollegeIdAndApplicationIdIn(s.getCollegeId(), List.of(a.getId())).stream()
                .collect(Collectors.toMap(RoundResult::getRoundId, RoundResult::getResult, (x, y) -> y));
        return roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(s.getCollegeId(), a.getDriveId()).stream()
                .map(r -> new PortalRound(r.getId(), r.getSeq(), r.getName(), r.getRoundType(), r.getStatus(), r.getScheduledAt(), results.get(r.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PortalInterview> interviews(UUID userId) {
        CollegeStudent s = me(userId);
        Map<UUID, CollegeApplication> apps = applicationRepository.findAllByCollegeIdAndStudentId(s.getCollegeId(), s.getId()).stream()
                .collect(Collectors.toMap(CollegeApplication::getId, a -> a));
        if (apps.isEmpty()) return List.of();
        List<InterviewSlot> slots = slotRepository.findAllByCollegeIdAndApplicationIdInOrderByStartsAtAsc(s.getCollegeId(), apps.keySet());
        Map<UUID, CollegeDrive> drives = new HashMap<>();
        Map<UUID, String> roundNames = new HashMap<>();
        for (InterviewSlot slot : slots) {
            CollegeDrive d = drives.computeIfAbsent(slot.getDriveId(), id -> directory.drive(s.getCollegeId(), id));
            if (slot.getRoundId() != null && !roundNames.containsKey(slot.getRoundId())) {
                roundRepository.findAllByCollegeIdAndDriveIdOrderBySeqAsc(s.getCollegeId(), d.getId()).forEach(r -> roundNames.put(r.getId(), r.getName()));
            }
        }
        var companies = directory.companies(s.getCollegeId(), drives.values().stream().map(CollegeDrive::getCompanyId).collect(Collectors.toSet()));
        return slots.stream().map(slot -> {
            CollegeDrive d = drives.get(slot.getDriveId());
            return new PortalInterview(slot.getId(), slot.getApplicationId(), Optional.ofNullable(companies.get(d.getCompanyId())).map(CollegeCompany::getName).orElse("-"),
                    d.getTitle(), slot.getRoundId() == null ? null : roundNames.get(slot.getRoundId()), slot.getStartsAt(), slot.getEndsAt(), slot.getRoom(),
                    slot.getMeetingLink(), slot.getPanelName(), slot.getStatus());
        }).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PortalAnnouncement> announcements(UUID userId, int page, int size) {
        CollegeStudent s = me(userId);
        var result = recipientRepository.findAllByCollegeIdAndStudentIdOrderByCreatedAtDesc(s.getCollegeId(), s.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
        Map<UUID, Announcement> anns = announcementRepository.findAllById(result.getContent().stream().map(AnnouncementRecipient::getAnnouncementId).toList())
                .stream().collect(Collectors.toMap(Announcement::getId, a -> a));
        Map<UUID, String> names = userRepository.findAllByIdIn(anns.values().stream().map(Announcement::getCreatedBy).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.of(result, r -> {
            Announcement a = anns.get(r.getAnnouncementId());
            return new PortalAnnouncement(a.getId(), a.getTitle(), a.getBody(), names.getOrDefault(a.getCreatedBy(), "Placement cell"), a.getCreatedAt(),
                    r.getReadAt() != null);
        });
    }

    @Transactional
    public void markAnnouncementRead(UUID userId, UUID announcementId) {
        CollegeStudent s = me(userId);
        AnnouncementRecipient r = recipientRepository.findByCollegeIdAndStudentIdAndAnnouncementId(s.getCollegeId(), s.getId(), announcementId)
                .orElseThrow(() -> CollegeDirectory.notFound("Announcement"));
        if (r.getReadAt() == null) {
            r.setReadAt(Instant.now());
            recipientRepository.save(r);
        }
    }

    @Transactional
    public void markAllAnnouncementsRead(UUID userId) {
        CollegeStudent s = me(userId);
        recipientRepository.markAllRead(s.getCollegeId(), s.getId(), Instant.now());
    }

    /** Placement staff the student may chat with: the TPO / admins and coordinators whose branch scope covers the student. Viewers are read-only. */
    @Transactional(readOnly = true)
    public List<PortalContact> contacts(UUID userId) {
        CollegeStudent s = me(userId);
        var members = memberRepository.findAllByCollegeIdOrderByCreatedAtAsc(s.getCollegeId()).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() != MemberRole.COLLEGE_VIEWER)
                .filter(m -> m.getRole() == MemberRole.COLLEGE_ADMIN || CollegeContextResolver.parseScope(m.getBranchScope()).isEmpty()
                        || (s.getBranchId() != null && CollegeContextResolver.parseScope(m.getBranchScope()).contains(s.getBranchId())))
                .toList();
        Map<UUID, User> users = userRepository.findAllByIdIn(members.stream().map(CollegeMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return members.stream().filter(m -> users.containsKey(m.getUserId())).map(m -> new PortalContact(m.getUserId(), users.get(m.getUserId()).getFullName(),
                m.getRole() == MemberRole.COLLEGE_ADMIN ? "TPO" : "COORDINATOR",
                m.getDesignation() != null ? m.getDesignation() : (m.getRole() == MemberRole.COLLEGE_ADMIN ? "Training & Placement Officer" : "Placement coordinator")))
                .toList();
    }

    /**
     * Student sign-up / account activation. The placement cell creates the student record; the student proves they own the
     * e-mail with a one-time code and then sets a password (via the standard reset-password endpoint).
     */
    @Transactional
    public ForgotPasswordResponse startActivation(String rawEmail, String rollNumber) {
        String email = AuthService.normalizeEmail(rawEmail);
        CollegeStudent s = studentRepository.findAllByEmailIgnoreCaseAndDeletedAtIsNull(email).stream()
                .filter(x -> x.getRollNumber().equalsIgnoreCase(rollNumber == null ? "" : rollNumber.trim())).findFirst()
                .orElseThrow(() -> new DomainException("No student record matches this e-mail and roll number. Check with your placement cell.",
                        HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND"));
        if (s.getUserId() == null) {
            s.setUserId(accountService.ensureCandidate(email, CollegeDirectory.fullName(s), s.getPhone()).userId());
            studentRepository.save(s);
        }
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(email);
        return authService.forgotPassword(req);
    }

    private StudentDocument ownDocument(CollegeStudent s, UUID docId) {
        StudentDocument d = documentRepository.findByIdAndCollegeId(docId, s.getCollegeId()).orElseThrow(() -> CollegeDirectory.notFound("Document"));
        if (!d.getStudentId().equals(s.getId())) throw CollegeDirectory.notFound("Document");
        return d;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private PortalApplication toApp(UUID collegeId, CollegeApplication a) {
        CollegeDrive d = directory.drive(collegeId, a.getDriveId());
        CollegeCompany c = directory.company(collegeId, d.getCompanyId());
        return new PortalApplication(a.getId(), d.getId(), d.getTitle(), c.getName(), a.getStatus(), a.getCurrentRound(), a.getCreatedAt());
    }
}
