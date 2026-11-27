package com.proveyu.college.application;

import com.proveyu.college.domain.*;
import com.proveyu.college.infrastructure.*;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tenant-scoped loaders. Every method takes the collegeId resolved from the principal and
 * returns 404 for rows of another college (no existence leak across tenants).
 */
@Component
@RequiredArgsConstructor
public class CollegeDirectory {

    private final CollegeProfileRepository profileRepository;
    private final CollegeBranchRepository branchRepository;
    private final CollegeTierRepository tierRepository;
    private final CollegeStudentRepository studentRepository;
    private final CollegeCompanyRepository companyRepository;
    private final CollegeDriveRepository driveRepository;
    private final DriveRoundRepository roundRepository;
    private final CollegeApplicationRepository applicationRepository;
    private final CollegeOfferRepository offerRepository;

    public static DomainException notFound(String what) {
        return new DomainException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public CollegeProfile profile(UUID collegeId) {
        return profileRepository.findById(collegeId).orElseThrow(() -> notFound("College"));
    }

    public CollegeStudent student(UUID collegeId, UUID id) {
        return studentRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Student"));
    }

    public CollegeCompany company(UUID collegeId, UUID id) {
        return companyRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Company"));
    }

    public CollegeDrive drive(UUID collegeId, UUID id) {
        return driveRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Drive"));
    }

    public DriveRound round(UUID collegeId, UUID id) {
        return roundRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Round"));
    }

    public CollegeApplication application(UUID collegeId, UUID id) {
        return applicationRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Application"));
    }

    public CollegeOffer offer(UUID collegeId, UUID id) {
        return offerRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Offer"));
    }

    public CollegeBranch branch(UUID collegeId, UUID id) {
        return branchRepository.findByIdAndCollegeId(id, collegeId).orElseThrow(() -> notFound("Branch"));
    }

    public List<CollegeTier> tiers(UUID collegeId) {
        return tierRepository.findAllByCollegeIdOrderByTierRankAsc(collegeId);
    }

    public Map<UUID, CollegeBranch> branches(UUID collegeId) {
        return branchRepository.findAllByCollegeIdOrderByNameAsc(collegeId).stream()
                .collect(Collectors.toMap(CollegeBranch::getId, Function.identity()));
    }

    public Map<UUID, CollegeCompany> companies(UUID collegeId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return companyRepository.findAllByIdInAndCollegeId(ids, collegeId).stream()
                .collect(Collectors.toMap(CollegeCompany::getId, Function.identity()));
    }

    public Map<UUID, CollegeDrive> drives(UUID collegeId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return driveRepository.findAllByIdInAndCollegeId(ids, collegeId).stream()
                .collect(Collectors.toMap(CollegeDrive::getId, Function.identity()));
    }

    public Map<UUID, CollegeStudent> students(UUID collegeId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return studentRepository.findAllByIdInAndCollegeId(ids, collegeId).stream()
                .collect(Collectors.toMap(CollegeStudent::getId, Function.identity()));
    }

    public static String fullName(CollegeStudent s) {
        return (s.getFirstName() + " " + (s.getLastName() == null ? "" : s.getLastName())).trim();
    }

    /** Students and company people of a suspended college are stopped the same way its staff and leaders are. */
    public void requireNotSuspended(java.util.UUID collegeId) {
        if (profile(collegeId).getApprovalStatus() == com.proveyu.college.domain.CollegeEnums.ApprovalStatus.SUSPENDED) {
            throw new com.proveyu.shared.error.DomainException("This college account is suspended by the PROVEYU team. Contact support.",
                    org.springframework.http.HttpStatus.FORBIDDEN, "COLLEGE_SUSPENDED");
        }
    }
}
