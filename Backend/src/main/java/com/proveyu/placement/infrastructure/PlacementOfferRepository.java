package com.proveyu.placement.infrastructure;

import com.proveyu.placement.domain.OfferStatus;
import com.proveyu.placement.domain.PlacementOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlacementOfferRepository extends JpaRepository<PlacementOffer, UUID> {
    List<PlacementOffer> findByDriveId(UUID driveId);
    List<PlacementOffer> findByStudentId(UUID studentId);
    Optional<PlacementOffer> findByDriveIdAndStudentId(UUID driveId, UUID studentId);
    boolean existsByDriveIdAndStudentId(UUID driveId, UUID studentId);
    List<PlacementOffer> findByStudentIdAndOfferStatus(UUID studentId, OfferStatus offerStatus);
}
