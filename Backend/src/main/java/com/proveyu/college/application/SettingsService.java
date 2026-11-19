package com.proveyu.college.application;

import com.proveyu.college.application.dto.SettingsDtos.*;
import com.proveyu.college.domain.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final CollegeProfileRepository profileRepository;
    private final CollegeBranchRepository branchRepository;
    private final CollegeBatchRepository batchRepository;
    private final CollegeTierRepository tierRepository;
    private final CollegeStudentRepository studentRepository;
    private final EmailTemplateRepository templateRepository;
    private final CollegeDirectory directory;
    private final CollegeFileService fileService;
    private final CollegeMailService mailService;
    private final AuditService auditService;

    // ---------------------------------------------------------------- profile
    @Transactional(readOnly = true)
    public ProfileResponse profile(CollegeContext ctx) {
        return toProfile(directory.profile(ctx.collegeId()));
    }

    @Transactional
    public ProfileResponse updateProfile(CollegeContext ctx, ProfileRequest r) {
        CollegeProfile p = directory.profile(ctx.collegeId());
        p.setCollegeName(r.collegeName().trim());
        p.setUniversity(r.university());
        p.setCity(r.city());
        p.setState(r.state());
        p.setWebsite(r.website());
        p.setTpoName(r.tpoName());
        p.setTpoPhone(r.tpoPhone());
        p.setCurrentAcademicYear(r.currentAcademicYear());
        p.setBrandColor(r.brandColor());
        profileRepository.save(p);
        auditService.log(ctx, "SETTINGS_PROFILE", "COLLEGE", ctx.collegeId(), "Updated college profile", null);
        return toProfile(p);
    }

    @Transactional
    public ProfileResponse uploadLogo(CollegeContext ctx, MultipartFile file) {
        CollegeProfile p = directory.profile(ctx.collegeId());
        CollegeFile f = fileService.store(ctx.collegeId(), ctx.userId(), file, "LOGO", CollegeFileService.IMAGE_EXT);
        p.setLogoFileId(f.getId());
        profileRepository.save(p);
        auditService.log(ctx, "SETTINGS_LOGO", "COLLEGE", ctx.collegeId(), "Updated college logo", null);
        return toProfile(p);
    }

    @Transactional(readOnly = true)
    public CollegeFile logo(UUID collegeId) {
        CollegeProfile p = directory.profile(collegeId);
        if (p.getLogoFileId() == null) throw CollegeDirectory.notFound("Logo");
        return fileService.get(collegeId, p.getLogoFileId());
    }

    private ProfileResponse toProfile(CollegeProfile p) {
        return new ProfileResponse(p.getCollegeId(), p.getCollegeName(), p.getAicteCode(), p.getUniversity(), p.getCity(), p.getState(),
                p.getEmailDomain(), p.getWebsite(), p.getTpoName(), p.getTpoEmail(), p.getTpoPhone(), p.getApprovalStatus(),
                p.getCurrentAcademicYear(), p.getLogoFileId() != null, p.getBrandColor());
    }

    // ---------------------------------------------------------------- policy
    @Transactional(readOnly = true)
    public PolicyDto policy(CollegeContext ctx) {
        CollegeProfile p = directory.profile(ctx.collegeId());
        return new PolicyDto(p.getPolicyMode(), p.getMaxApplications(), p.getMaxOffers(), p.getUpgradeMinHikePercent(), p.isBlockAfterAccept(),
                p.getOffCampusPolicy());
    }

    @Transactional
    public PolicyDto updatePolicy(CollegeContext ctx, PolicyDto r) {
        CollegeProfile p = directory.profile(ctx.collegeId());
        String before = p.getPolicyMode() + "/maxApps=" + p.getMaxApplications() + "/maxOffers=" + p.getMaxOffers() + "/hike=" + p.getUpgradeMinHikePercent()
                + "/offCampus=" + p.getOffCampusPolicy();
        if (r.offCampusPolicy() != null) p.setOffCampusPolicy(r.offCampusPolicy());
        p.setPolicyMode(r.policyMode());
        p.setMaxApplications(r.maxApplications());
        p.setMaxOffers(r.maxOffers());
        p.setUpgradeMinHikePercent(r.upgradeMinHikePercent());
        p.setBlockAfterAccept(r.blockAfterAccept());
        profileRepository.save(p);
        auditService.log(ctx, "SETTINGS_POLICY", "COLLEGE", ctx.collegeId(), "Updated placement policy to " + r.policyMode(), "before: " + before);
        return policy(ctx);
    }

    // ---------------------------------------------------------------- branches
    @Transactional(readOnly = true)
    public List<BranchResponse> branches(CollegeContext ctx) {
        Map<UUID, Long> counts = studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .filter(s -> s.getBranchId() != null).collect(Collectors.groupingBy(CollegeStudent::getBranchId, Collectors.counting()));
        return branchRepository.findAllByCollegeIdOrderByNameAsc(ctx.collegeId()).stream()
                .map(b -> new BranchResponse(b.getId(), b.getCode(), b.getName(), counts.getOrDefault(b.getId(), 0L))).toList();
    }

    @Transactional
    public BranchResponse addBranch(CollegeContext ctx, BranchRequest r) {
        if (branchRepository.existsByCollegeIdAndCodeIgnoreCase(ctx.collegeId(), r.code().trim())) {
            throw new DomainException("Branch code " + r.code() + " already exists", HttpStatus.CONFLICT, "DUPLICATE_BRANCH");
        }
        CollegeBranch b = branchRepository.save(CollegeBranch.builder().collegeId(ctx.collegeId()).code(r.code().trim().toUpperCase(Locale.ROOT)).name(r.name().trim()).build());
        auditService.log(ctx, "BRANCH_ADDED", "BRANCH", b.getId(), "Added branch " + b.getCode(), null);
        return new BranchResponse(b.getId(), b.getCode(), b.getName(), 0);
    }

    @Transactional
    public BranchResponse updateBranch(CollegeContext ctx, UUID id, BranchRequest r) {
        CollegeBranch b = directory.branch(ctx.collegeId(), id);
        branchRepository.findByCollegeIdAndCodeIgnoreCase(ctx.collegeId(), r.code().trim()).filter(x -> !x.getId().equals(id)).ifPresent(x -> {
            throw new DomainException("Branch code " + r.code() + " already exists", HttpStatus.CONFLICT, "DUPLICATE_BRANCH");
        });
        b.setCode(r.code().trim().toUpperCase(Locale.ROOT));
        b.setName(r.name().trim());
        branchRepository.save(b);
        return branches(ctx).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteBranch(CollegeContext ctx, UUID id) {
        CollegeBranch b = directory.branch(ctx.collegeId(), id);
        if (studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream().anyMatch(s -> id.equals(s.getBranchId()))) {
            throw new DomainException("Branch has students; move them first", HttpStatus.CONFLICT, "BRANCH_IN_USE");
        }
        branchRepository.delete(b);
        auditService.log(ctx, "BRANCH_DELETED", "BRANCH", id, "Deleted branch " + b.getCode(), null);
    }

    // ---------------------------------------------------------------- batches
    @Transactional(readOnly = true)
    public List<BatchResponse> batches(CollegeContext ctx) {
        Map<Integer, Long> counts = studentRepository.findAllByCollegeIdAndDeletedAtIsNull(ctx.collegeId()).stream()
                .collect(Collectors.groupingBy(CollegeStudent::getBatchYear, Collectors.counting()));
        return batchRepository.findAllByCollegeIdOrderByGradYearDesc(ctx.collegeId()).stream()
                .map(b -> new BatchResponse(b.getId(), b.getGradYear(), b.getLabel(), b.isActive(), counts.getOrDefault(b.getGradYear(), 0L))).toList();
    }

    @Transactional
    public BatchResponse saveBatch(CollegeContext ctx, UUID id, BatchRequest r) {
        CollegeBatch b;
        if (id == null) {
            if (batchRepository.existsByCollegeIdAndGradYear(ctx.collegeId(), r.gradYear())) {
                throw new DomainException("Batch " + r.gradYear() + " already exists", HttpStatus.CONFLICT, "DUPLICATE_BATCH");
            }
            b = CollegeBatch.builder().collegeId(ctx.collegeId()).gradYear(r.gradYear()).build();
        } else {
            b = batchRepository.findByIdAndCollegeId(id, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Batch"));
        }
        b.setLabel(r.label());
        b.setActive(r.active());
        b = batchRepository.save(b);
        auditService.log(ctx, "BATCH_SAVED", "BATCH", b.getId(), "Saved batch " + b.getGradYear(), null);
        UUID saved = b.getId();
        return batches(ctx).stream().filter(x -> x.id().equals(saved)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteBatch(CollegeContext ctx, UUID id) {
        CollegeBatch b = batchRepository.findByIdAndCollegeId(id, ctx.collegeId()).orElseThrow(() -> CollegeDirectory.notFound("Batch"));
        batchRepository.delete(b);
    }

    // ---------------------------------------------------------------- tiers
    @Transactional(readOnly = true)
    public List<TierDto> tiers(CollegeContext ctx) {
        return directory.tiers(ctx.collegeId()).stream().map(t -> new TierDto(t.getId(), t.getName(), t.getTierRank(), t.getMinCtcLpa())).toList();
    }

    /** Replaces the tier ladder. Ranks must be unique and min CTC must increase with rank. */
    @Transactional
    public List<TierDto> saveTiers(CollegeContext ctx, TiersRequest r) {
        List<TierDto> sorted = r.tiers().stream().sorted(Comparator.comparing(TierDto::tierRank)).toList();
        Set<Integer> ranks = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (!ranks.add(sorted.get(i).tierRank())) throw new DomainException("Tier ranks must be unique", HttpStatus.BAD_REQUEST, "BAD_TIERS");
            if (i > 0 && sorted.get(i).minCtcLpa().compareTo(sorted.get(i - 1).minCtcLpa()) <= 0) {
                throw new DomainException("Minimum CTC must increase with tier rank", HttpStatus.BAD_REQUEST, "BAD_TIERS");
            }
        }
        Map<UUID, CollegeTier> existing = directory.tiers(ctx.collegeId()).stream().collect(Collectors.toMap(CollegeTier::getId, t -> t));
        Set<UUID> keep = sorted.stream().map(TierDto::id).filter(Objects::nonNull).collect(Collectors.toSet());
        // move ranks out of the way first to respect the unique (college_id, tier_rank) key
        existing.values().forEach(t -> { t.setTierRank(t.getTierRank() + 100); tierRepository.saveAndFlush(t); });
        existing.values().stream().filter(t -> !keep.contains(t.getId())).forEach(tierRepository::delete);
        tierRepository.flush();
        for (TierDto d : sorted) {
            CollegeTier t = d.id() != null && existing.containsKey(d.id()) ? existing.get(d.id())
                    : CollegeTier.builder().collegeId(ctx.collegeId()).build();
            t.setName(d.name().trim());
            t.setTierRank(d.tierRank());
            t.setMinCtcLpa(d.minCtcLpa());
            tierRepository.saveAndFlush(t);
        }
        auditService.log(ctx, "SETTINGS_TIERS", "COLLEGE", ctx.collegeId(), "Updated tiers: " + sorted.stream().map(t -> t.name() + ">=" + t.minCtcLpa())
                .collect(Collectors.joining(", ")), null);
        return tiers(ctx);
    }

    // ---------------------------------------------------------------- e-mail templates
    @Transactional(readOnly = true)
    public List<TemplateDto> templates(CollegeContext ctx) {
        Map<String, EmailTemplate> custom = templateRepository.findAllByCollegeIdOrderByTemplateKeyAsc(ctx.collegeId()).stream()
                .collect(Collectors.toMap(EmailTemplate::getTemplateKey, t -> t));
        return CollegeMailService.DEFAULTS.entrySet().stream().map(e -> {
            EmailTemplate c = custom.get(e.getKey());
            return c == null ? new TemplateDto(e.getKey(), e.getValue().subject(), e.getValue().body(), false)
                    : new TemplateDto(e.getKey(), c.getSubject(), c.getBody(), true);
        }).toList();
    }

    @Transactional
    public TemplateDto saveTemplate(CollegeContext ctx, String key, TemplateDto r) {
        if (!CollegeMailService.DEFAULTS.containsKey(key)) throw CollegeDirectory.notFound("Template");
        mailService.upsert(ctx.collegeId(), key, r.subject(), r.body());
        auditService.log(ctx, "SETTINGS_TEMPLATE", "COLLEGE", ctx.collegeId(), "Updated e-mail template " + key, null);
        return new TemplateDto(key, r.subject(), r.body(), true);
    }

    @Transactional
    public void resetTemplate(CollegeContext ctx, String key) {
        templateRepository.findByCollegeIdAndTemplateKey(ctx.collegeId(), key).ifPresent(templateRepository::delete);
    }
}
