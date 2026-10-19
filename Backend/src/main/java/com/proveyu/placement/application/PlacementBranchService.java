package com.proveyu.placement.application;

import com.proveyu.placement.domain.Branch;
import com.proveyu.placement.infrastructure.BranchRepository;
import com.proveyu.placement.web.dto.PlacementDtos.BranchRequest;
import com.proveyu.placement.web.dto.PlacementDtos.BranchResponse;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlacementBranchService {

    private final BranchRepository branchRepository;

    @Transactional
    public BranchResponse createBranch(UUID collegeId, BranchRequest request) {
        branchRepository.findByCollegeIdAndBranchCode(collegeId, request.getBranchCode())
                .ifPresent(existing -> {
                    throw new DomainException("Branch with code '" + request.getBranchCode() + "' already exists for this college", HttpStatus.CONFLICT, "BRANCH_EXISTS");
                });

        Branch branch = Branch.builder()
                .collegeId(collegeId)
                .branchName(request.getBranchName())
                .branchCode(request.getBranchCode())
                .build();

        Branch saved = branchRepository.save(branch);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches(UUID collegeId) {
        return branchRepository.findByCollegeId(collegeId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranch(UUID collegeId, UUID branchId) {
        Branch branch = branchRepository.findByCollegeIdAndBranchId(collegeId, branchId)
                .orElseThrow(() -> new DomainException("Branch not found", HttpStatus.NOT_FOUND, "BRANCH_NOT_FOUND"));
        return toResponse(branch);
    }

    @Transactional
    public void deleteBranch(UUID collegeId, UUID branchId) {
        Branch branch = branchRepository.findByCollegeIdAndBranchId(collegeId, branchId)
                .orElseThrow(() -> new DomainException("Branch not found", HttpStatus.NOT_FOUND, "BRANCH_NOT_FOUND"));
        branchRepository.delete(branch);
    }

    private BranchResponse toResponse(Branch branch) {
        return BranchResponse.builder()
                .branchId(branch.getBranchId())
                .collegeId(branch.getCollegeId())
                .branchName(branch.getBranchName())
                .branchCode(branch.getBranchCode())
                .createdAt(branch.getCreatedAt())
                .build();
    }
}
