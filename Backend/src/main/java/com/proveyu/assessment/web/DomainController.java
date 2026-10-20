package com.proveyu.assessment.web;

import com.proveyu.assessment.domain.Domain;
import com.proveyu.assessment.infrastructure.DomainRepository;
import com.proveyu.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainRepository domainRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Domain>>> getActiveDomains() {
        List<Domain> domains = domainRepository.findByActiveTrueOrderByNameAsc();
        return ResponseEntity.ok(ApiResponse.success(domains, "Active domains retrieved successfully"));
    }
}
