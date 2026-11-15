package com.proveyu.college.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.college.application.dto.CompanyDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.domain.CollegeEnums.MouStatus;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.config.AppProperties;
import com.proveyu.shared.email.EmailService;
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
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final Set<String> SORTABLE = Set.of("name", "industry", "createdAt", "mouStatus");

    private final CollegeCompanyRepository companyRepository;
    private final CompanyContactRepository contactRepository;
    private final CompanyFeedbackRepository feedbackRepository;
    private final CollegeDriveRepository driveRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeOfferRepository offerRepository;
    private final UserRepository userRepository;
    private final CollegeDirectory directory;
    private final AuditService auditService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    public Specification<CollegeCompany> spec(UUID collegeId, String q, String industry, MouStatus mou, boolean deleted) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("collegeId"), collegeId));
            p.add(deleted ? cb.isNotNull(root.get("deletedAt")) : cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) p.add(cb.like(cb.lower(root.get("name")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            if (industry != null && !industry.isBlank()) p.add(cb.equal(cb.lower(root.get("industry")), industry.toLowerCase(Locale.ROOT)));
            if (mou != null) p.add(cb.equal(root.get("mouStatus"), mou));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> page(CollegeContext ctx, String q, String industry, MouStatus mou, boolean deleted,
                                              int page, int size, String sort, String dir) {
        Sort s = Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, SORTABLE.contains(sort) ? sort : "name");
        var result = companyRepository.findAll(spec(ctx.collegeId(), q, industry, mou, deleted), PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200), s));
        Stats stats = stats(ctx.collegeId());
        return PageResponse.of(result, c -> toResponse(c, stats));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> all(CollegeContext ctx, String q, String industry, MouStatus mou) {
        Stats stats = stats(ctx.collegeId());
        return companyRepository.findAll(spec(ctx.collegeId(), q, industry, mou, false), Sort.by("name")).stream().map(c -> toResponse(c, stats)).toList();
    }

    @Transactional(readOnly = true)
    public CompanyDetail detail(CollegeContext ctx, UUID id) {
        CollegeCompany c = directory.company(ctx.collegeId(), id);
        Stats stats = stats(ctx.collegeId());
        var drives = driveRepository.findAllByCollegeIdAndCompanyIdAndDeletedAtIsNull(ctx.collegeId(), id);
        var apps = applicationRepository.findAllByCollegeId(ctx.collegeId());
        var offers = offerRepository.findAllByCollegeId(ctx.collegeId());
        var visits = drives.stream().sorted(Comparator.comparing(CollegeDrive::getCreatedAt).reversed()).map(d -> new VisitHistory(d.getId(), d.getTitle(),
                d.getRoleTitle(), d.getStatus(), d.getDriveDate(), d.getCtcLpa(),
                (int) apps.stream().filter(a -> a.getDriveId().equals(d.getId())).count(),
                (int) offers.stream().filter(o -> d.getId().equals(o.getDriveId())).count())).toList();
        return new CompanyDetail(toResponse(c, stats), contacts(ctx, id), visits, feedback(ctx, id));
    }

    @Transactional
    public CompanyResponse create(CollegeContext ctx, CompanyRequest r) {
        if (companyRepository.existsByCollegeIdAndNameIgnoreCase(ctx.collegeId(), r.name().trim())) {
            throw new DomainException("A company named '" + r.name() + "' already exists", HttpStatus.CONFLICT, "DUPLICATE_COMPANY");
        }
        CollegeCompany c = CollegeCompany.builder().collegeId(ctx.collegeId()).build();
        apply(c, r);
        c = companyRepository.save(c);
        auditService.log(ctx, "COMPANY_CREATED", "COMPANY", c.getId(), "Added company " + c.getName(), null);
        return toResponse(c, stats(ctx.collegeId()));
    }

    @Transactional
    public CompanyResponse update(CollegeContext ctx, UUID id, CompanyRequest r) {
        CollegeCompany c = directory.company(ctx.collegeId(), id);
        if (!c.getName().equalsIgnoreCase(r.name().trim()) && companyRepository.existsByCollegeIdAndNameIgnoreCase(ctx.collegeId(), r.name().trim())) {
            throw new DomainException("A company named '" + r.name() + "' already exists", HttpStatus.CONFLICT, "DUPLICATE_COMPANY");
        }
        apply(c, r);
        companyRepository.save(c);
        auditService.log(ctx, "COMPANY_UPDATED", "COMPANY", c.getId(), "Updated company " + c.getName(), null);
        return toResponse(c, stats(ctx.collegeId()));
    }

    @Transactional
    public void softDelete(CollegeContext ctx, UUID id) {
        CollegeCompany c = directory.company(ctx.collegeId(), id);
        c.setDeletedAt(Instant.now());
        companyRepository.save(c);
        auditService.log(ctx, "COMPANY_DELETED", "COMPANY", id, "Moved company " + c.getName() + " to trash", null);
    }

    @Transactional
    public CompanyResponse restore(CollegeContext ctx, UUID id) {
        CollegeCompany c = directory.company(ctx.collegeId(), id);
        c.setDeletedAt(null);
        companyRepository.save(c);
        auditService.log(ctx, "COMPANY_RESTORED", "COMPANY", id, "Restored company " + c.getName(), null);
        return toResponse(c, stats(ctx.collegeId()));
    }

    // ---- contacts
    @Transactional(readOnly = true)
    public List<ContactResponse> contacts(CollegeContext ctx, UUID companyId) {
        return contactRepository.findAllByCollegeIdAndCompanyIdOrderByCreatedAtAsc(ctx.collegeId(), companyId).stream()
                .map(x -> new ContactResponse(x.getId(), x.getName(), x.getDesignation(), x.getEmail(), x.getPhone(), x.isPrimaryContact())).toList();
    }

    @Transactional
    public ContactResponse addContact(CollegeContext ctx, UUID companyId, ContactRequest r) {
        directory.company(ctx.collegeId(), companyId);
        if (r.primaryContact()) clearPrimary(ctx.collegeId(), companyId);
        CompanyContact x = contactRepository.save(CompanyContact.builder().collegeId(ctx.collegeId()).companyId(companyId)
                .name(r.name()).designation(r.designation()).email(r.email()).phone(r.phone()).primaryContact(r.primaryContact()).build());
        return new ContactResponse(x.getId(), x.getName(), x.getDesignation(), x.getEmail(), x.getPhone(), x.isPrimaryContact());
    }

    @Transactional
    public ContactResponse updateContact(CollegeContext ctx, UUID contactId, ContactRequest r) {
        CompanyContact x = contactRepository.findByIdAndCollegeId(contactId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Contact"));
        if (r.primaryContact() && !x.isPrimaryContact()) clearPrimary(ctx.collegeId(), x.getCompanyId());
        x.setName(r.name());
        x.setDesignation(r.designation());
        x.setEmail(r.email());
        x.setPhone(r.phone());
        x.setPrimaryContact(r.primaryContact());
        contactRepository.save(x);
        return new ContactResponse(x.getId(), x.getName(), x.getDesignation(), x.getEmail(), x.getPhone(), x.isPrimaryContact());
    }

    @Transactional
    public void deleteContact(CollegeContext ctx, UUID contactId) {
        CompanyContact x = contactRepository.findByIdAndCollegeId(contactId, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Contact"));
        contactRepository.delete(x);
    }

    private void clearPrimary(UUID collegeId, UUID companyId) {
        contactRepository.findAllByCollegeIdAndCompanyIdOrderByCreatedAtAsc(collegeId, companyId).stream().filter(CompanyContact::isPrimaryContact)
                .forEach(c -> { c.setPrimaryContact(false); contactRepository.save(c); });
    }

    // ---- feedback
    @Transactional(readOnly = true)
    public List<FeedbackResponse> feedback(CollegeContext ctx, UUID companyId) {
        var list = feedbackRepository.findAllByCollegeIdAndCompanyIdOrderByCreatedAtDesc(ctx.collegeId(), companyId);
        var drives = directory.drives(ctx.collegeId(), list.stream().map(CompanyFeedback::getDriveId).filter(Objects::nonNull).collect(Collectors.toSet()));
        var users = userRepository.findAllByIdIn(list.stream().map(CompanyFeedback::getCreatedBy).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return list.stream().map(f -> new FeedbackResponse(f.getId(), f.getDriveId(),
                f.getDriveId() == null || drives.get(f.getDriveId()) == null ? null : drives.get(f.getDriveId()).getTitle(),
                f.getRating(), f.getComment(), users.getOrDefault(f.getCreatedBy(), "-"), f.getCreatedAt())).toList();
    }

    @Transactional
    public FeedbackResponse addFeedback(CollegeContext ctx, UUID companyId, FeedbackRequest r) {
        directory.company(ctx.collegeId(), companyId);
        if (r.driveId() != null) {
            CollegeDrive d = directory.drive(ctx.collegeId(), r.driveId());
            if (!d.getCompanyId().equals(companyId)) throw new DomainException("Drive does not belong to this company", HttpStatus.BAD_REQUEST, "DRIVE_MISMATCH");
        }
        CompanyFeedback f = feedbackRepository.save(CompanyFeedback.builder().collegeId(ctx.collegeId()).companyId(companyId).driveId(r.driveId())
                .rating(r.rating()).comment(r.comment()).createdBy(ctx.userId()).build());
        auditService.log(ctx, "COMPANY_FEEDBACK", "COMPANY", companyId, "Rated company " + r.rating() + "/5", r.comment());
        return feedback(ctx, companyId).stream().filter(x -> x.id().equals(f.getId())).findFirst().orElseThrow();
    }

    // ---- recruiter link / invite
    @Transactional(readOnly = true)
    public List<RecruiterOption> searchRecruiters(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        return userRepository.searchRecruiters(q.trim(), PageRequest.of(0, 10)).stream()
                .map(u -> new RecruiterOption(u.getId(), u.getFullName(), u.getEmail())).toList();
    }

    @Transactional
    public void invite(CollegeContext ctx, UUID companyId, InviteCompanyRequest r) {
        CollegeCompany c = directory.company(ctx.collegeId(), companyId);
        CollegeProfile p = directory.profile(ctx.collegeId());
        String body = "<p>Hello,</p><p><b>" + HtmlUtils.htmlEscape(p.getCollegeName()) + "</b> invites <b>" + HtmlUtils.htmlEscape(c.getName())
                + "</b> to recruit from our campus on PROVEYU.</p>"
                + (r.message() == null ? "" : "<p>" + HtmlUtils.htmlEscape(r.message()).replace("\n", "<br>") + "</p>")
                + "<p>Create your free recruiter account: <a href=\"" + appProperties.getFrontendUrl() + "/select-role\">"
                + appProperties.getFrontendUrl() + "/select-role</a></p><p>Regards,<br>" + HtmlUtils.htmlEscape(ctx.actorName()) + "</p>";
        emailService.sendHtml(r.email(), "Campus recruitment invitation from " + p.getCollegeName(), body);
        if (c.getMouStatus() == MouStatus.NONE) {
            c.setMouStatus(MouStatus.IN_PROGRESS);
            companyRepository.save(c);
        }
        auditService.log(ctx, "COMPANY_INVITED", "COMPANY", companyId, "Invited " + c.getName() + " (" + r.email() + ")", null);
    }

    // ---- helpers
    private record Stats(Map<UUID, Long> drives, Map<UUID, Long> offers, Map<UUID, Double> rating, Map<UUID, CompanyContact> primary, Map<UUID, String> recruiterNames) {}

    private Stats stats(UUID collegeId) {
        Map<UUID, Long> drives = driveRepository.findAllByCollegeIdAndDeletedAtIsNull(collegeId).stream()
                .collect(Collectors.groupingBy(CollegeDrive::getCompanyId, Collectors.counting()));
        Map<UUID, Long> offers = offerRepository.findAllByCollegeId(collegeId).stream()
                .filter(o -> EligibilityEngine.ACTIVE_OFFER.contains(o.getStatus()))
                .collect(Collectors.groupingBy(CollegeOffer::getCompanyId, Collectors.counting()));
        Map<UUID, Double> rating = feedbackRepository.findAllByCollegeId(collegeId).stream()
                .collect(Collectors.groupingBy(CompanyFeedback::getCompanyId, Collectors.averagingInt(CompanyFeedback::getRating)));
        Map<UUID, CompanyContact> primary = new HashMap<>();
        contactRepository.findAll((root, q, cb) -> cb.and(cb.equal(root.get("collegeId"), collegeId), cb.isTrue(root.get("primaryContact"))))
                .forEach(c -> primary.put(c.getCompanyId(), c));
        Set<UUID> recruiterIds = companyRepository.findAllByCollegeIdAndDeletedAtIsNullOrderByNameAsc(collegeId).stream()
                .map(CollegeCompany::getLinkedRecruiterId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllByIdIn(recruiterIds).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return new Stats(drives, offers, rating, primary, names);
    }

    private CompanyResponse toResponse(CollegeCompany c, Stats s) {
        CompanyContact pc = s.primary().get(c.getId());
        Double avg = s.rating().get(c.getId());
        return new CompanyResponse(c.getId(), c.getName(), c.getIndustry(), c.getWebsite(), c.getHqLocation(), c.getMouStatus(), c.getMouExpiry(),
                c.getLinkedRecruiterId(), c.getLinkedRecruiterId() == null ? null : s.recruiterNames().get(c.getLinkedRecruiterId()), c.getNotes(),
                s.drives().getOrDefault(c.getId(), 0L).intValue(), s.offers().getOrDefault(c.getId(), 0L).intValue(),
                avg == null ? null : Math.round(avg * 10) / 10.0, pc == null ? null : pc.getName(), pc == null ? null : pc.getEmail(),
                c.getDeletedAt() != null, c.getCreatedAt());
    }

    private void apply(CollegeCompany c, CompanyRequest r) {
        c.setName(r.name().trim());
        c.setIndustry(r.industry());
        c.setWebsite(r.website());
        c.setHqLocation(r.hqLocation());
        c.setMouStatus(r.mouStatus() == null ? MouStatus.NONE : r.mouStatus());
        c.setMouExpiry(r.mouExpiry());
        if (r.linkedRecruiterId() != null) {
            User u = userRepository.findById(r.linkedRecruiterId())
                    .filter(x -> x.getRole() == com.proveyu.auth.domain.Role.RECRUITER)
                    .orElseThrow(() -> new DomainException("Linked recruiter not found", HttpStatus.BAD_REQUEST, "RECRUITER_NOT_FOUND"));
            c.setLinkedRecruiterId(u.getId());
        } else {
            c.setLinkedRecruiterId(null);
        }
        c.setNotes(r.notes());
    }
}
