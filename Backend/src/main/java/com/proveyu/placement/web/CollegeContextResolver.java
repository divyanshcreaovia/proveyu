package com.proveyu.placement.web;

import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CollegeContextResolver {

    private final OrganizationRepository organizationRepository;

    public UUID resolveCollegeId(Authentication authentication, UUID explicitCollegeId) {
        if (explicitCollegeId != null) {
            return explicitCollegeId;
        }
        if (authentication == null || authentication.getName() == null) {
            throw new DomainException("Unauthenticated request", HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid user ID in authentication principal", HttpStatus.UNAUTHORIZED, "INVALID_AUTH");
        }

        return organizationRepository.findByUserId(userId)
                .map(Organization::getId)
                .orElse(userId);
    }
}
