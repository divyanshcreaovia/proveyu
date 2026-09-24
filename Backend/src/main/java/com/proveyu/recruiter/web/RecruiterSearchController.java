package com.proveyu.recruiter.web;

import com.proveyu.recruiter.application.SearchCandidatesService;
import com.proveyu.recruiter.domain.CandidateSearchIndex;
import com.proveyu.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiters/candidates")
@RequiredArgsConstructor
public class RecruiterSearchController {

    private final SearchCandidatesService searchCandidatesService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CandidateSearchIndex>>> searchCandidates(
            @RequestParam(required = false) UUID skillId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<CandidateSearchIndex> results = searchCandidatesService.searchCandidates(skillId, pageable);
        return ResponseEntity.ok(ApiResponse.success(results, "Candidate search completed successfully"));
    }
}
