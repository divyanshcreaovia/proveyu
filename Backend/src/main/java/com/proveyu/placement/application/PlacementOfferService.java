package com.proveyu.placement.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.placement.domain.*;
import com.proveyu.placement.infrastructure.*;
import com.proveyu.placement.web.dto.PlacementDtos.CreateOffersRequest;
import com.proveyu.placement.web.dto.PlacementDtos.PlacementOfferResponse;
import com.proveyu.placement.web.dto.PlacementDtos.UpdateOfferStatusRequest;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementOfferService {

    private final PlacementOfferRepository offerRepository;
    private final PlacementDriveRepository driveRepository;
    private final DriveRoundRepository driveRoundRepository;
    private final DriveRoundStudentRepository driveRoundStudentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    /**
     * Create placement offers for SELECTED students of the final round.
     * Idempotent (does not re-insert for students who already have an offer for this drive).
     */
    @Transactional
    public List<PlacementOfferResponse> createOffersFromFinalRound(UUID collegeId, UUID driveId, CreateOffersRequest request) {
        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                .orElseThrow(() -> new DomainException("Placement drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));

        List<DriveRound> rounds = driveRoundRepository.findByDriveIdOrderByRoundNumberAsc(driveId);
        if (rounds.isEmpty()) {
            throw new DomainException("Drive has no rounds configured", HttpStatus.BAD_REQUEST, "NO_ROUNDS");
        }

        // Final round is the one with the highest round_number
        DriveRound lastRound = rounds.stream()
                .max(Comparator.comparingInt(DriveRound::getRoundNumber))
                .orElseThrow();

        List<DriveRoundStudent> selectedStudents = driveRoundStudentRepository
                .findByDriveRoundIdAndStatus(lastRound.getDriveRoundId(), RoundStudentStatus.SELECTED);

        if (selectedStudents.isEmpty()) {
            throw new DomainException("No SELECTED students found in final Round " + lastRound.getRoundNumber(),
                    HttpStatus.BAD_REQUEST, "NO_SELECTED_STUDENTS");
        }

        BigDecimal packageLpa = (request != null && request.getPackageLpa() != null)
                ? request.getPackageLpa() : drive.getOfferedPackage();

        for (DriveRoundStudent drs : selectedStudents) {
            if (!offerRepository.existsByDriveIdAndStudentId(driveId, drs.getStudentId())) {
                PlacementOffer offer = PlacementOffer.builder()
                        .driveId(driveId)
                        .studentId(drs.getStudentId())
                        .roleName(request != null ? request.getRoleName() : null)
                        .packageLpa(packageLpa)
                        .offerStatus(OfferStatus.OFFERED)
                        .build();
                offerRepository.save(offer);
            }
        }

        drive.setStatus(DriveStatus.COMPLETED);
        driveRepository.save(drive);

        return listOffers(collegeId, driveId, null);
    }

    /**
     * Update an offer's status (OFFERED -> ACCEPTED | REJECTED).
     * If ACCEPTED, updates student's placedStatus = PLACED and highestPackageLpa.
     */
    @Transactional
    public PlacementOfferResponse updateOfferStatus(UUID collegeId, UUID offerId, UpdateOfferStatusRequest request) {
        PlacementOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new DomainException("Placement offer not found", HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND"));

        PlacementDrive drive = driveRepository.findByDriveIdAndCollegeId(offer.getDriveId(), collegeId)
                .orElseThrow(() -> new DomainException("Unauthorized access to offer", HttpStatus.FORBIDDEN, "UNAUTHORIZED_OFFER"));

        offer.setOfferStatus(request.getOfferStatus());
        PlacementOffer savedOffer = offerRepository.save(offer);

        if (request.getOfferStatus() == OfferStatus.ACCEPTED) {
            studentRepository.findByStudentId(offer.getStudentId()).ifPresent(student -> {
                student.setPlacedStatus(PlacedStatus.PLACED);
                if (student.getHighestPackageLpa() == null || offer.getPackageLpa().compareTo(student.getHighestPackageLpa()) > 0) {
                    student.setHighestPackageLpa(offer.getPackageLpa());
                }
                studentRepository.save(student);
            });
        }

        return toOfferResponse(savedOffer, drive);
    }

    @Transactional(readOnly = true)
    public List<PlacementOfferResponse> listOffers(UUID collegeId, UUID driveId, UUID studentId) {
        List<PlacementOffer> offers;
        if (driveId != null) {
            driveRepository.findByDriveIdAndCollegeId(driveId, collegeId)
                    .orElseThrow(() -> new DomainException("Drive not found", HttpStatus.NOT_FOUND, "DRIVE_NOT_FOUND"));
            offers = offerRepository.findByDriveId(driveId);
        } else if (studentId != null) {
            studentRepository.findByCollegeIdAndStudentId(collegeId, studentId)
                    .orElseThrow(() -> new DomainException("Student not found", HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND"));
            offers = offerRepository.findByStudentId(studentId);
        } else {
            // All offers for drives of this college
            List<PlacementDrive> collegeDrives = driveRepository.findByCollegeId(collegeId);
            List<UUID> driveIds = collegeDrives.stream().map(PlacementDrive::getDriveId).collect(Collectors.toList());
            offers = driveIds.stream()
                    .flatMap(did -> offerRepository.findByDriveId(did).stream())
                    .collect(Collectors.toList());
        }

        return offers.stream()
                .map(offer -> {
                    PlacementDrive drive = driveRepository.findById(offer.getDriveId()).orElse(null);
                    return toOfferResponse(offer, drive);
                })
                .collect(Collectors.toList());
    }

    private PlacementOfferResponse toOfferResponse(PlacementOffer offer, PlacementDrive drive) {
        String companyName = "Unknown Company";
        if (drive != null) {
            companyName = companyRepository.findById(drive.getCompanyId())
                    .map(Company::getName)
                    .orElse("Unknown Company");
        }

        User user = userRepository.findById(offer.getStudentId()).orElse(null);

        return PlacementOfferResponse.builder()
                .offerId(offer.getOfferId())
                .driveId(offer.getDriveId())
                .companyName(companyName)
                .studentId(offer.getStudentId())
                .studentName(user != null ? user.getFullName() : null)
                .studentEmail(user != null ? user.getEmail() : null)
                .roleName(offer.getRoleName())
                .packageLpa(offer.getPackageLpa())
                .offerStatus(offer.getOfferStatus())
                .offeredAt(offer.getOfferedAt())
                .build();
    }
}
