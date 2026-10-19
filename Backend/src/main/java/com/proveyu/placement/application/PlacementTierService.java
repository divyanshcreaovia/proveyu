package com.proveyu.placement.application;

import com.proveyu.placement.domain.Tier;
import com.proveyu.placement.infrastructure.TierRepository;
import com.proveyu.placement.web.dto.PlacementDtos.TierRequest;
import com.proveyu.placement.web.dto.PlacementDtos.TierResponse;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlacementTierService {

    private final TierRepository tierRepository;

    @Transactional
    public TierResponse createTier(UUID collegeId, TierRequest request) {
        tierRepository.findByCollegeIdAndTierLevel(collegeId, request.getTierLevel())
                .ifPresent(existing -> {
                    throw new DomainException("Tier level " + request.getTierLevel() + " already exists for this college", HttpStatus.CONFLICT, "TIER_LEVEL_EXISTS");
                });

        Tier tier = Tier.builder()
                .collegeId(collegeId)
                .tierLevel(request.getTierLevel())
                .tierName(request.getTierName())
                .minScore(request.getMinScore())
                .build();

        return toResponse(tierRepository.save(tier));
    }

    @Transactional(readOnly = true)
    public List<TierResponse> listTiers(UUID collegeId) {
        return tierRepository.findByCollegeIdOrderByTierLevelAsc(collegeId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TierResponse getTier(UUID collegeId, UUID tierId) {
        Tier tier = tierRepository.findByCollegeIdAndTierId(collegeId, tierId)
                .orElseThrow(() -> new DomainException("Tier not found", HttpStatus.NOT_FOUND, "TIER_NOT_FOUND"));
        return toResponse(tier);
    }

    @Transactional
    public TierResponse updateTier(UUID collegeId, UUID tierId, TierRequest request) {
        Tier tier = tierRepository.findByCollegeIdAndTierId(collegeId, tierId)
                .orElseThrow(() -> new DomainException("Tier not found", HttpStatus.NOT_FOUND, "TIER_NOT_FOUND"));

        if (tier.getTierLevel() != request.getTierLevel()) {
            tierRepository.findByCollegeIdAndTierLevel(collegeId, request.getTierLevel())
                    .ifPresent(existing -> {
                        throw new DomainException("Tier level " + request.getTierLevel() + " already exists for this college", HttpStatus.CONFLICT, "TIER_LEVEL_EXISTS");
                    });
        }

        tier.setTierLevel(request.getTierLevel());
        tier.setTierName(request.getTierName());
        tier.setMinScore(request.getMinScore());
        return toResponse(tierRepository.save(tier));
    }

    @Transactional
    public void deleteTier(UUID collegeId, UUID tierId) {
        Tier tier = tierRepository.findByCollegeIdAndTierId(collegeId, tierId)
                .orElseThrow(() -> new DomainException("Tier not found", HttpStatus.NOT_FOUND, "TIER_NOT_FOUND"));
        tierRepository.delete(tier);
    }

    /**
     * Tier of a student is derived, never stored.
     * A student belongs to the best tier (lowest tier_level) of their college where student.score >= tier.min_score.
     * Students below the lowest threshold have no tier (returns empty).
     */
    @Transactional(readOnly = true)
    public Optional<Tier> deriveTier(UUID collegeId, BigDecimal score) {
        if (score == null) {
            return Optional.empty();
        }
        List<Tier> tiers = tierRepository.findByCollegeIdOrderByTierLevelAsc(collegeId);
        // tiers are ordered by tierLevel ASC (1 is best, then 2, then 3...)
        return tiers.stream()
                .filter(t -> score.compareTo(t.getMinScore()) >= 0)
                .min(Comparator.comparingInt(Tier::getTierLevel));
    }

    public TierResponse toResponse(Tier tier) {
        return TierResponse.builder()
                .tierId(tier.getTierId())
                .collegeId(tier.getCollegeId())
                .tierLevel(tier.getTierLevel())
                .tierName(tier.getTierName())
                .minScore(tier.getMinScore())
                .createdAt(tier.getCreatedAt())
                .build();
    }
}
