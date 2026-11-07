package com.proveyu.placement.application;

import com.proveyu.placement.application.dto.PlacementDriveDto;
import com.proveyu.placement.domain.PlacementDrive1;
import com.proveyu.placement.infrastructure.PlacementDriveRepository1;
import com.proveyu.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PlacementDriveService1 {

    private final PlacementDriveRepository1 driveRepository;
    private final PlacementManagementService placementManagementService;

    public PlacementDriveService1(PlacementDriveRepository1 driveRepository,
                                 PlacementManagementService placementManagementService) {
        this.driveRepository = driveRepository;
        this.placementManagementService = placementManagementService;
    }

    @Transactional(readOnly = true)
    public List<PlacementDriveDto.Response> getDrives(UUID currentUserId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);
        return driveRepository.findByOrganizationIdOrderByCreatedAtDesc(ctx.organizationId)
                .stream().map(PlacementDriveDto.Response::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public PlacementDriveDto.Response getDriveById(UUID currentUserId, UUID driveId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);
        PlacementDrive1 drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new DomainException("Drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        if (!drive.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Drive does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        return PlacementDriveDto.Response.fromEntity(drive);
    }

    @Transactional
    public PlacementDriveDto.Response createDrive(UUID currentUserId, PlacementDriveDto.Request req) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);

        PlacementDrive1 drive = new PlacementDrive1();
        drive.setOrganizationId(ctx.organizationId);
        drive.setCompanyName(req.getCompanyName());
        drive.setCompanyWebsite(req.getCompanyWebsite());
        drive.setLogoUrl(req.getLogoUrl());
        drive.setIndustry(req.getIndustry());
        drive.setRoles(req.getRoles());
        drive.setJobLocation(req.getJobLocation());
        drive.setEmploymentType(req.getEmploymentType() != null ? req.getEmploymentType() : "Full-Time");
        drive.setPackageLpa(req.getPackageLpa() != null ? req.getPackageLpa() : 0.0);
        drive.setStipendMonthly(req.getStipendMonthly());
        drive.setBondDurationMonths(req.getBondDurationMonths() != null ? req.getBondDurationMonths() : 0);
        drive.setDriveCategory(req.getDriveCategory());
        drive.setDriveDate(req.getDriveDate());
        drive.setDriveMode(req.getDriveMode() != null ? req.getDriveMode() : "On-Campus");
        drive.setVenue(req.getVenue());
        drive.setMinCgpa(req.getMinCgpa() != null ? req.getMinCgpa() : 0.0);
        drive.setMaxBacklogs(req.getMaxBacklogs() != null ? req.getMaxBacklogs() : 0);
        drive.setMaxGapYears(req.getMaxGapYears() != null ? req.getMaxGapYears() : 0);
        drive.setMinTenthPercentage(req.getMinTenthPercentage() != null ? req.getMinTenthPercentage() : 0.0);
        drive.setMinTwelfthPercentage(req.getMinTwelfthPercentage() != null ? req.getMinTwelfthPercentage() : 0.0);
        drive.setBatchFilter(req.getBatchFilter() != null ? req.getBatchFilter() : "All");
        drive.setDegreeFilter(req.getDegreeFilter() != null ? req.getDegreeFilter() : "All");
        drive.setMinProveYuScore(req.getMinProveYuScore() != null ? req.getMinProveYuScore() : 0);
        drive.setEligibleBranches(req.getEligibleBranches());
        drive.setSelectionRounds(req.getSelectionRounds());
        drive.setHrContactName(req.getHrContactName());
        drive.setHrContactEmail(req.getHrContactEmail());
        drive.setHrContactPhone(req.getHrContactPhone());
        drive.setPlacementEligibility(req.getPlacementEligibility() != null ? req.getPlacementEligibility() : "Unplaced Only");
        drive.setAllowPlacedStudents(Boolean.TRUE.equals(req.getAllowPlacedStudents()));
        drive.setStatus(req.getStatus() != null ? req.getStatus() : "Scheduled");

        PlacementDrive1 saved = driveRepository.save(drive);
        return PlacementDriveDto.Response.fromEntity(saved);
    }

    @Transactional
    public PlacementDriveDto.Response updateDrive(UUID currentUserId, UUID driveId, PlacementDriveDto.Request req) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);
        PlacementDrive1 drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new DomainException("Drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        if (!drive.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Drive does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        if (req.getCompanyName() != null) drive.setCompanyName(req.getCompanyName());
        if (req.getRoles() != null) drive.setRoles(req.getRoles());
        if (req.getPackageLpa() != null) drive.setPackageLpa(req.getPackageLpa());
        if (req.getDriveDate() != null) drive.setDriveDate(req.getDriveDate());
        if (req.getVenue() != null) drive.setVenue(req.getVenue());
        if (req.getMinCgpa() != null) drive.setMinCgpa(req.getMinCgpa());
        if (req.getMaxBacklogs() != null) drive.setMaxBacklogs(req.getMaxBacklogs());
        if (req.getEligibleBranches() != null) drive.setEligibleBranches(req.getEligibleBranches());
        if (req.getSelectionRounds() != null) drive.setSelectionRounds(req.getSelectionRounds());
        if (req.getStatus() != null) drive.setStatus(req.getStatus());

        PlacementDrive1 saved = driveRepository.save(drive);
        return PlacementDriveDto.Response.fromEntity(saved);
    }

    @Transactional
    public void deleteDrive(UUID currentUserId, UUID driveId) {
        PlacementManagementService.OrgUserContext ctx = placementManagementService.resolveContext(currentUserId);
        PlacementDrive1 drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new DomainException("Drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        if (!drive.getOrganizationId().equals(ctx.organizationId)) {
            throw new DomainException("Drive does not belong to your institution", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        driveRepository.delete(drive);
    }
}
