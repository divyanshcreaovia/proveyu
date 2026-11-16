package com.proveyu.college.application;

import com.proveyu.assessment.infrastructure.SkillRepository;
import com.proveyu.candidate.infrastructure.CandidateProfileRepository;
import com.proveyu.candidate.infrastructure.CandidateScoreRepository;
import com.proveyu.college.application.dto.StudentDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.config.AppProperties;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private static final Set<String> SORTABLE = Set.of("rollNumber", "firstName", "cgpa", "batchYear", "createdAt", "highestCtcLpa", "status", "activeBacklogs");

    private final CollegeStudentRepository studentRepository;
    private final StudentDocumentRepository documentRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeOfferRepository offerRepository;
    private final CollegeDirectory directory;
    private final CollegeFileService fileService;
    private final StudentAccountService accountService;
    private final AuditService auditService;
    private final CollegeMailService mailService;
    private final AppProperties appProperties;
    private final CandidateScoreRepository candidateScoreRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final SkillRepository skillRepository;

    // ------------------------------------------------------------------ queries

    public Specification<CollegeStudent> spec(CollegeContext ctx, StudentFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("collegeId"), ctx.collegeId()));
            p.add(f.deleted() ? cb.isNotNull(root.get("deletedAt")) : cb.isNull(root.get("deletedAt")));
            if (!ctx.branchScope().isEmpty()) p.add(root.get("branchId").in(ctx.branchScope()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("rollNumber")), like), cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like), cb.like(cb.lower(root.get("email")), like)));
            }
            if (f.batchYear() != null) p.add(cb.equal(root.get("batchYear"), f.batchYear()));
            if (f.branchId() != null) p.add(cb.equal(root.get("branchId"), f.branchId()));
            if (f.minCgpa() != null) p.add(cb.greaterThanOrEqualTo(root.get("cgpa"), f.minCgpa()));
            if (f.maxCgpa() != null) p.add(cb.lessThanOrEqualTo(root.get("cgpa"), f.maxCgpa()));
            if (f.maxBacklogs() != null) p.add(cb.lessThanOrEqualTo(root.get("activeBacklogs"), f.maxBacklogs()));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.placed() != null) p.add(f.placed() ? cb.equal(root.get("status"), StudentStatus.PLACED) : cb.notEqual(root.get("status"), StudentStatus.PLACED));
            if (f.skill() != null && !f.skill().isBlank()) p.add(cb.like(cb.lower(root.get("skills")), "%" + f.skill().trim().toLowerCase(Locale.ROOT) + "%"));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> page(CollegeContext ctx, StudentFilter f, int page, int size, String sort, String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "rollNumber";
        Sort s = Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortField);
        var result = studentRepository.findAll(spec(ctx, f), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), s));
        Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
        return PageResponse.of(result, st -> toResponse(st, branches));
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> all(CollegeContext ctx, StudentFilter f) {
        Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
        return studentRepository.findAll(spec(ctx, f), Sort.by("rollNumber")).stream().map(s -> toResponse(s, branches)).toList();
    }

    @Transactional(readOnly = true)
    public StudentDetail detail(CollegeContext ctx, UUID id) {
        CollegeStudent s = scoped(ctx, id);
        Map<UUID, CollegeBranch> branches = directory.branches(ctx.collegeId());
        List<CollegeApplication> apps = applicationRepository.findAllByCollegeIdAndStudentId(ctx.collegeId(), id);
        List<CollegeOffer> offers = offerRepository.findAllByCollegeIdAndStudentId(ctx.collegeId(), id);
        Map<UUID, CollegeDrive> drives = directory.drives(ctx.collegeId(), apps.stream().map(CollegeApplication::getDriveId).collect(Collectors.toSet()));
        Set<UUID> companyIds = new HashSet<>();
        drives.values().forEach(d -> companyIds.add(d.getCompanyId()));
        offers.forEach(o -> companyIds.add(o.getCompanyId()));
        Map<UUID, CollegeCompany> companies = directory.companies(ctx.collegeId(), companyIds);

        var appSummaries = apps.stream().map(a -> {
            CollegeDrive d = drives.get(a.getDriveId());
            return new StudentApplicationSummary(a.getId(), a.getDriveId(), d == null ? "-" : d.getTitle(),
                    d == null ? "-" : Optional.ofNullable(companies.get(d.getCompanyId())).map(CollegeCompany::getName).orElse("-"),
                    a.getStatus(), a.getCurrentRound(), a.getCreatedAt());
        }).toList();
        var offerSummaries = offers.stream().map(o -> new StudentOfferSummary(o.getId(),
                Optional.ofNullable(companies.get(o.getCompanyId())).map(CollegeCompany::getName).orElse("-"),
                o.getRoleTitle(), o.getCtcLpa(), o.getStatus(), o.getOfferDate())).toList();
        return new StudentDetail(toResponse(s, branches), documents(ctx, id), appSummaries, offerSummaries, passport(s));
    }

    /** "Proveyu passport": verified skill scores from the assessment module + candidate profile completeness. */
    private Passport passport(CollegeStudent s) {
        List<SkillScore> scores = List.of();
        String resumeUrl = null, headline = null;
        List<String> candidateSkills = List.of();
        if (s.getUserId() != null) {
            var raw = candidateScoreRepository.findByCandidateId(s.getUserId());
            Map<UUID, String> skillNames = new HashMap<>();
            skillRepository.findAllById(raw.stream().map(c -> c.getSkillId()).filter(Objects::nonNull).collect(Collectors.toSet()))
                    .forEach(sk -> skillNames.put(sk.getId(), sk.getName()));
            scores = raw.stream().map(c -> new SkillScore(skillNames.getOrDefault(c.getSkillId(), "Assessment"), c.getScore(), c.getMaxScore(), c.isPassed())).toList();
            var profile = candidateProfileRepository.findByUserId(s.getUserId());
            if (profile.isPresent()) {
                resumeUrl = profile.get().getResumeUrl();
                headline = profile.get().getHeadline();
                candidateSkills = splitSkills(profile.get().getSkillsList());
            }
        }
        Double avg = scores.isEmpty() ? null : scores.stream().filter(x -> x.maxScore() > 0).mapToDouble(x -> x.score() * 100.0 / x.maxScore()).average().orElse(0);
        List<String> missing = new ArrayList<>();
        if (s.getCgpa() == null) missing.add("CGPA");
        if (s.getTenthPercent() == null) missing.add("10th %");
        if (s.getTwelfthPercent() == null) missing.add("12th %");
        if (s.getPhone() == null) missing.add("Phone");
        if (splitSkills(s.getSkills()).isEmpty()) missing.add("Skills");
        if (resumeUrl == null && documentRepository.findAllByCollegeIdAndStudentIdOrderByCreatedAtDesc(s.getCollegeId(), s.getId()).stream()
                .noneMatch(d -> d.getDocType() == DocType.RESUME)) missing.add("Resume");
        if (s.getLinkedinUrl() == null) missing.add("LinkedIn");
        if (scores.isEmpty()) missing.add("PROVEYU verified assessment");
        int completeness = (int) Math.round(100.0 * (8 - missing.size()) / 8);
        return new Passport(scores, avg, resumeUrl, headline, candidateSkills, completeness, missing);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public StudentResponse create(CollegeContext ctx, StudentRequest r) {
        CollegeBranch branch = directory.branch(ctx.collegeId(), r.branchId());
        requireBranch(ctx, branch.getId());
        assertUnique(ctx.collegeId(), null, r.rollNumber(), r.email());
        var account = accountService.ensureCandidate(r.email(), (r.firstName() + " " + Objects.toString(r.lastName(), "")).trim(), r.phone());
        CollegeStudent s = CollegeStudent.builder().collegeId(ctx.collegeId()).userId(account.userId()).build();
        apply(s, r);
        s = studentRepository.save(s);
        auditService.log(ctx, "STUDENT_CREATED", "STUDENT", s.getId(), "Added student " + s.getRollNumber() + " (" + CollegeDirectory.fullName(s) + ")", null);
        sendWelcome(ctx.collegeId(), s);
        return toResponse(s, directory.branches(ctx.collegeId()));
    }

    void sendWelcome(UUID collegeId, CollegeStudent s) {
        CollegeProfile profile = directory.profile(collegeId);
        mailService.send(collegeId, "STUDENT_WELCOME", s.getEmail(), Map.of("studentName", CollegeDirectory.fullName(s),
                "collegeName", profile.getCollegeName(), "email", s.getEmail(), "portalUrl", appProperties.getFrontendUrl() + "/login"));
    }

    @Transactional
    public StudentResponse update(CollegeContext ctx, UUID id, StudentRequest r) {
        CollegeStudent s = scoped(ctx, id);
        directory.branch(ctx.collegeId(), r.branchId());
        requireBranch(ctx, r.branchId());
        assertUnique(ctx.collegeId(), id, r.rollNumber(), r.email());
        if (!s.getEmail().equalsIgnoreCase(r.email())) {
            var account = accountService.ensureCandidate(r.email(), (r.firstName() + " " + Objects.toString(r.lastName(), "")).trim(), r.phone());
            s.setUserId(account.userId());
        }
        apply(s, r);
        s = studentRepository.save(s);
        auditService.log(ctx, "STUDENT_UPDATED", "STUDENT", s.getId(), "Updated student " + s.getRollNumber(), null);
        return toResponse(s, directory.branches(ctx.collegeId()));
    }

    @Transactional
    public void softDelete(CollegeContext ctx, UUID id) {
        CollegeStudent s = scoped(ctx, id);
        s.setDeletedAt(Instant.now());
        studentRepository.save(s);
        auditService.log(ctx, "STUDENT_DELETED", "STUDENT", id, "Moved student " + s.getRollNumber() + " to trash", null);
    }

    @Transactional
    public StudentResponse restore(CollegeContext ctx, UUID id) {
        CollegeStudent s = scoped(ctx, id);
        assertUnique(ctx.collegeId(), id, s.getRollNumber(), s.getEmail());
        s.setDeletedAt(null);
        studentRepository.save(s);
        auditService.log(ctx, "STUDENT_RESTORED", "STUDENT", id, "Restored student " + s.getRollNumber(), null);
        return toResponse(s, directory.branches(ctx.collegeId()));
    }

    @Transactional
    public StudentResponse changeStatus(CollegeContext ctx, UUID id, StatusChangeRequest r) {
        if (r.status() == StudentStatus.PLACED) {
            throw new DomainException("PLACED is set automatically from accepted offers", HttpStatus.BAD_REQUEST, "STATUS_DERIVED");
        }
        CollegeStudent s = scoped(ctx, id);
        StudentStatus old = s.getStatus();
        s.setStatus(r.status());
        studentRepository.save(s);
        // restoring to ACTIVE re-derives PLACED from offers
        if (r.status() == StudentStatus.ACTIVE) recomputePlacement(ctx.collegeId(), s);
        auditService.log(ctx, "STUDENT_STATUS", "STUDENT", id, "Status " + old + " -> " + r.status() + " for " + s.getRollNumber(), r.reason());
        return toResponse(s, directory.branches(ctx.collegeId()));
    }

    /** Derives PLACED / highest CTC / highest tier from the student's offers. DEBARRED / OPTED_OUT are preserved. */
    @Transactional
    public void recomputePlacement(UUID collegeId, CollegeStudent s) {
        List<CollegeOffer> offers = offerRepository.findAllByCollegeIdAndStudentId(collegeId, s.getId());
        var active = offers.stream().filter(o -> EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus())).toList();
        s.setHighestCtcLpa(active.stream().map(CollegeOffer::getCtcLpa).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
        s.setHighestTierRank(active.stream().map(CollegeOffer::getTierRank).filter(Objects::nonNull).max(Integer::compare).orElse(null));
        boolean placed = active.stream().anyMatch(o -> o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.JOINED);
        if (s.getStatus() == StudentStatus.ACTIVE || s.getStatus() == StudentStatus.PLACED) {
            s.setStatus(placed ? StudentStatus.PLACED : StudentStatus.ACTIVE);
        }
        studentRepository.save(s);
    }

    // ------------------------------------------------------------------ documents

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(CollegeContext ctx, UUID studentId) {
        return documents(ctx.collegeId(), studentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(UUID collegeId, UUID studentId) {
        return documentRepository.findAllByCollegeIdAndStudentIdOrderByCreatedAtDesc(collegeId, studentId).stream().map(d -> {
            CollegeFile f = fileService.get(collegeId, d.getFileId());
            return new DocumentResponse(d.getId(), d.getDocType(), d.getLabel(), f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                    d.getVerificationStatus(), d.getRemarks(), d.getVerifiedAt(), d.getCreatedAt());
        }).toList();
    }

    @Transactional
    public DocumentResponse uploadDocument(UUID collegeId, UUID actorId, UUID studentId, DocType type, String label, MultipartFile file) {
        CollegeFile stored = fileService.store(collegeId, actorId, file, "STUDENT_DOC", CollegeFileService.DOC_EXT);
        String cleanLabel = label == null || label.isBlank() ? null : label.trim().substring(0, Math.min(label.trim().length(), 150));
        StudentDocument d = documentRepository.save(StudentDocument.builder().collegeId(collegeId).studentId(studentId).docType(type).label(cleanLabel)
                .fileId(stored.getId()).build());
        return new DocumentResponse(d.getId(), d.getDocType(), d.getLabel(), stored.getId(), stored.getOriginalName(), stored.getContentType(), stored.getSizeBytes(),
                d.getVerificationStatus(), d.getRemarks(), d.getVerifiedAt(), d.getCreatedAt());
    }

    @Transactional
    public DocumentResponse uploadDocument(CollegeContext ctx, UUID studentId, DocType type, MultipartFile file) {
        CollegeStudent s = scoped(ctx, studentId);
        DocumentResponse r = uploadDocument(ctx.collegeId(), ctx.userId(), s.getId(), type, null, file);
        auditService.log(ctx, "DOCUMENT_UPLOADED", "STUDENT", studentId, "Uploaded " + type + " for " + s.getRollNumber(), r.fileName());
        return r;
    }

    @Transactional
    public DocumentResponse verifyDocument(CollegeContext ctx, UUID docId, VerifyDocumentRequest r) {
        StudentDocument d = documentRepository.findByIdAndCollegeId(docId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Document"));
        CollegeStudent s = scoped(ctx, d.getStudentId());
        d.setVerificationStatus(r.status());
        d.setRemarks(r.remarks());
        d.setVerifiedBy(ctx.userId());
        d.setVerifiedAt(Instant.now());
        documentRepository.save(d);
        auditService.log(ctx, "DOCUMENT_" + r.status(), "STUDENT", s.getId(), d.getDocType() + " of " + s.getRollNumber() + " marked " + r.status(), r.remarks());
        return documents(ctx, s.getId()).stream().filter(x -> x.id().equals(docId)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteDocument(CollegeContext ctx, UUID docId) {
        StudentDocument d = documentRepository.findByIdAndCollegeId(docId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Document"));
        CollegeStudent s = scoped(ctx, d.getStudentId());
        documentRepository.delete(d);
        auditService.log(ctx, "DOCUMENT_DELETED", "STUDENT", s.getId(), "Deleted " + d.getDocType() + " of " + s.getRollNumber(), null);
    }

    @Transactional(readOnly = true)
    public CollegeFile documentFile(CollegeContext ctx, UUID docId) {
        StudentDocument d = documentRepository.findByIdAndCollegeId(docId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Document"));
        scoped(ctx, d.getStudentId());
        return fileService.get(ctx.collegeId(), d.getFileId());
    }

    // ------------------------------------------------------------------ helpers

    /** Loads a student of the caller's college and enforces coordinator branch scope. */
    public CollegeStudent scoped(CollegeContext ctx, UUID id) {
        CollegeStudent s = directory.student(ctx.collegeId(), id);
        if (!ctx.canAccessBranch(s.getBranchId())) throw CollegeDirectory.notFound("Student");
        return s;
    }

    private void requireBranch(CollegeContext ctx, UUID branchId) {
        if (!ctx.canAccessBranch(branchId)) {
            throw new DomainException("You can only manage students of your assigned branches", HttpStatus.FORBIDDEN, "BRANCH_SCOPE");
        }
    }

    private void assertUnique(UUID collegeId, UUID selfId, String roll, String email) {
        studentRepository.findByCollegeIdAndRollNumberIgnoreCase(collegeId, roll.trim()).filter(x -> !x.getId().equals(selfId)).ifPresent(x -> {
            throw new DomainException("Roll number " + roll + " already exists", HttpStatus.CONFLICT, "DUPLICATE_ROLL");
        });
        studentRepository.findByCollegeIdAndEmailIgnoreCase(collegeId, email.trim()).filter(x -> !x.getId().equals(selfId)).ifPresent(x -> {
            throw new DomainException("E-mail " + email + " already belongs to student " + x.getRollNumber(), HttpStatus.CONFLICT, "DUPLICATE_EMAIL");
        });
    }

    private void apply(CollegeStudent s, StudentRequest r) {
        s.setRollNumber(r.rollNumber().trim());
        s.setFirstName(r.firstName().trim());
        s.setLastName(r.lastName() == null ? null : r.lastName().trim());
        s.setEmail(r.email().trim().toLowerCase(Locale.ROOT));
        s.setPhone(r.phone());
        s.setGender(r.gender());
        s.setDob(r.dob());
        s.setBranchId(r.branchId());
        s.setBatchYear(r.batchYear());
        s.setDegree(r.degree());
        s.setCgpa(r.cgpa());
        s.setTenthPercent(r.tenthPercent());
        s.setTwelfthPercent(r.twelfthPercent());
        s.setActiveBacklogs(r.activeBacklogs() == null ? 0 : r.activeBacklogs());
        s.setGapYears(r.gapYears() == null ? 0 : r.gapYears());
        s.setSkills(r.skills() == null ? null : String.join(", ", r.skills().stream().map(String::trim).filter(x -> !x.isEmpty()).distinct().toList()));
        s.setLinkedinUrl(r.linkedinUrl());
        s.setGithubUrl(r.githubUrl());
    }

    public static List<String> splitSkills(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split("[,;]")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public StudentResponse toResponse(CollegeStudent s, Map<UUID, CollegeBranch> branches) {
        CollegeBranch b = s.getBranchId() == null ? null : branches.get(s.getBranchId());
        return new StudentResponse(s.getId(), s.getRollNumber(), s.getFirstName(), s.getLastName(), CollegeDirectory.fullName(s), s.getEmail(),
                s.getPhone(), s.getGender(), s.getDob(), s.getBranchId(), b == null ? null : b.getName(), b == null ? null : b.getCode(),
                s.getBatchYear(), s.getDegree(), s.getCgpa(), s.getTenthPercent(), s.getTwelfthPercent(), s.getActiveBacklogs(), s.getGapYears(),
                splitSkills(s.getSkills()), s.getLinkedinUrl(), s.getGithubUrl(), s.getStatus(), s.getHighestCtcLpa(), s.getHighestTierRank(),
                s.getUserId() != null, s.getDeletedAt() != null, s.getCreatedAt(), s.getUpdatedAt());
    }
}
