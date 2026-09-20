package com.proveyu.organization.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.organization.domain.OrgType;
import com.proveyu.organization.domain.Organization;
import com.proveyu.organization.domain.OrganizationCandidate;
import com.proveyu.organization.infrastructure.OrganizationCandidateRepository;
import com.proveyu.organization.infrastructure.OrganizationRepository;
import com.proveyu.recruiter.domain.CandidateSearchIndex;
import com.proveyu.recruiter.infrastructure.CandidateSearchIndexRepository;
import com.proveyu.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationCandidateRepository organizationCandidateRepository;
    private final UserRepository userRepository;
    private final CandidateSearchIndexRepository searchIndexRepository;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationCandidateRepository organizationCandidateRepository,
                               UserRepository userRepository,
                               CandidateSearchIndexRepository searchIndexRepository,
                               PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.organizationCandidateRepository = organizationCandidateRepository;
        this.userRepository = userRepository;
        this.searchIndexRepository = searchIndexRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Organization createOrganizationProfile(UUID userId, String legalName, OrgType orgType, String codeOrGstin, String contactEmail, String contactPhone, String address) {
        Organization organization = organizationRepository.findByUserId(userId).orElse(
                Organization.builder()
                        .userId(userId)
                        .legalName(legalName)
                        .orgType(orgType != null ? orgType : OrgType.COLLEGE)
                        .codeOrGstin(codeOrGstin)
                        .contactEmail(contactEmail)
                        .contactPhone(contactPhone)
                        .address(address)
                        .verified(true)
                        .build()
        );
        organization.setLegalName(legalName);
        organization.setOrgType(orgType);
        organization.setCodeOrGstin(codeOrGstin);
        organization.setContactEmail(contactEmail);
        organization.setContactPhone(contactPhone);
        organization.setAddress(address);

        return organizationRepository.save(organization);
    }

    @Transactional
    public User registerCandidateUnderOrganization(UUID orgUserId, String email, String fullName, String rawPassword, String rollNumberOrId, String batchYear) {
        Organization organization = organizationRepository.findByUserId(orgUserId)
                .orElseThrow(() -> new DomainException("Organization profile not found. Please create profile first.", HttpStatus.BAD_REQUEST, "ORG_NOT_FOUND"));

        User candidate = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .fullName(fullName)
                    .passwordHash(passwordEncoder.encode(rawPassword != null ? rawPassword : "Pass@" + System.currentTimeMillis()))
                    .role(Role.CANDIDATE)
                    .active(true)
                    .build();
            return userRepository.save(newUser);
        });

        organizationCandidateRepository.findByOrganizationIdAndCandidateId(organization.getId(), candidate.getId())
                .ifPresent(existing -> {
                    throw new DomainException("Candidate is already registered under this organization", HttpStatus.BAD_REQUEST, "ALREADY_REGISTERED_UNDER_ORG");
                });

        OrganizationCandidate mapping = OrganizationCandidate.builder()
                .organizationId(organization.getId())
                .candidateId(candidate.getId())
                .rollNumberOrId(rollNumberOrId)
                .batchYear(batchYear)
                .build();

        organizationCandidateRepository.save(mapping);
        return candidate;
    }

    @Transactional(readOnly = true)
    public List<OrganizationCandidate> getOrganizationCandidates(UUID orgUserId) {
        Organization organization = organizationRepository.findByUserId(orgUserId)
                .orElseThrow(() -> new DomainException("Organization profile not found", HttpStatus.NOT_FOUND, "ORG_NOT_FOUND"));

        return organizationCandidateRepository.findByOrganizationId(organization.getId());
    }

    @Transactional(readOnly = true)
    public List<CandidateSearchIndex> getCandidateScorecards(UUID orgUserId, UUID candidateId) {
        Organization organization = organizationRepository.findByUserId(orgUserId)
                .orElseThrow(() -> new DomainException("Organization profile not found", HttpStatus.NOT_FOUND, "ORG_NOT_FOUND"));

        organizationCandidateRepository.findByOrganizationIdAndCandidateId(organization.getId(), candidateId)
                .orElseThrow(() -> new DomainException("Candidate does not belong to your organization", HttpStatus.FORBIDDEN, "UNAUTHORIZED_CANDIDATE_ACCESS"));

        return searchIndexRepository.findAll((root, query, cb) -> cb.equal(root.get("candidateId"), candidateId));
    }
}
