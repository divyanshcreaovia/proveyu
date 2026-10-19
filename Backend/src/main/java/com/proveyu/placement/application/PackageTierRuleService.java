package com.proveyu.placement.application;

import com.proveyu.placement.domain.PackageTierRule;
import com.proveyu.placement.domain.Tier;
import com.proveyu.placement.infrastructure.PackageTierRuleRepository;
import com.proveyu.placement.infrastructure.TierRepository;
import com.proveyu.placement.web.dto.PlacementDtos.PackageTierRuleRequest;
import com.proveyu.placement.web.dto.PlacementDtos.PackageTierRuleResponse;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageTierRuleService {

    private final PackageTierRuleRepository ruleRepository;
    private final TierRepository tierRepository;

    @Transactional
    public PackageTierRuleResponse createRule(UUID collegeId, PackageTierRuleRequest request) {
        if (request.getMaxPackageLpa() != null && request.getMaxPackageLpa().compareTo(request.getMinPackageLpa()) <= 0) {
            throw new DomainException("maxPackageLpa must be greater than minPackageLpa", HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        }

        Tier tier = tierRepository.findByCollegeIdAndTierId(collegeId, request.getTierId())
                .orElseThrow(() -> new DomainException("Tier not found for this college", HttpStatus.NOT_FOUND, "TIER_NOT_FOUND"));

        PackageTierRule rule = PackageTierRule.builder()
                .collegeId(collegeId)
                .minPackageLpa(request.getMinPackageLpa())
                .maxPackageLpa(request.getMaxPackageLpa())
                .tierId(tier.getTierId())
                .build();

        return toResponse(ruleRepository.save(rule), tier.getTierName());
    }

    @Transactional(readOnly = true)
    public List<PackageTierRuleResponse> listRules(UUID collegeId) {
        return ruleRepository.findByCollegeIdOrderByMinPackageLpaAsc(collegeId).stream()
                .map(rule -> {
                    String tierName = tierRepository.findById(rule.getTierId())
                            .map(Tier::getTierName)
                            .orElse("Unknown Tier");
                    return toResponse(rule, tierName);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRule(UUID collegeId, UUID ruleId) {
        PackageTierRule rule = ruleRepository.findByCollegeIdAndRuleId(collegeId, ruleId)
                .orElseThrow(() -> new DomainException("Rule not found", HttpStatus.NOT_FOUND, "RULE_NOT_FOUND"));
        ruleRepository.delete(rule);
    }

    /**
     * Looks up package_tier_rule for the college using offered_package.
     * Range is [min, max). max_package_lpa NULL means unbounded upper limit.
     */
    @Transactional(readOnly = true)
    public List<UUID> getApplicableTierIds(UUID collegeId, BigDecimal offeredPackage) {
        if (offeredPackage == null) {
            return List.of();
        }
        return ruleRepository.findByCollegeId(collegeId).stream()
                .filter(rule -> offeredPackage.compareTo(rule.getMinPackageLpa()) >= 0
                        && (rule.getMaxPackageLpa() == null || offeredPackage.compareTo(rule.getMaxPackageLpa()) < 0))
                .map(PackageTierRule::getTierId)
                .distinct()
                .collect(Collectors.toList());
    }

    private PackageTierRuleResponse toResponse(PackageTierRule rule, String tierName) {
        return PackageTierRuleResponse.builder()
                .ruleId(rule.getRuleId())
                .collegeId(rule.getCollegeId())
                .minPackageLpa(rule.getMinPackageLpa())
                .maxPackageLpa(rule.getMaxPackageLpa())
                .tierId(rule.getTierId())
                .tierName(tierName)
                .createdAt(rule.getCreatedAt())
                .build();
    }
}
