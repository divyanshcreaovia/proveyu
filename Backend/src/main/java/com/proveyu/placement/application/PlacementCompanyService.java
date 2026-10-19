package com.proveyu.placement.application;

import com.proveyu.placement.domain.Company;
import com.proveyu.placement.infrastructure.CompanyRepository;
import com.proveyu.placement.web.dto.PlacementDtos.CompanyRequest;
import com.proveyu.placement.web.dto.PlacementDtos.CompanyResponse;
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
public class PlacementCompanyService {

    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyResponse createCompany(CompanyRequest request) {
        if (companyRepository.existsByName(request.getName())) {
            throw new DomainException("Company with name '" + request.getName() + "' already exists", HttpStatus.CONFLICT, "COMPANY_EXISTS");
        }
        Company company = Company.builder()
                .name(request.getName())
                .category(request.getCategory())
                .website(request.getWebsite())
                .build();
        Company saved = companyRepository.save(company);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies() {
        return companyRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new DomainException("Company not found with ID: " + companyId, HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND"));
        return toResponse(company);
    }

    @Transactional
    public CompanyResponse updateCompany(UUID companyId, CompanyRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new DomainException("Company not found with ID: " + companyId, HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND"));

        if (!company.getName().equalsIgnoreCase(request.getName()) && companyRepository.existsByName(request.getName())) {
            throw new DomainException("Company with name '" + request.getName() + "' already exists", HttpStatus.CONFLICT, "COMPANY_EXISTS");
        }

        company.setName(request.getName());
        company.setCategory(request.getCategory());
        company.setWebsite(request.getWebsite());
        return toResponse(companyRepository.save(company));
    }

    @Transactional
    public void deleteCompany(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new DomainException("Company not found with ID: " + companyId, HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND");
        }
        companyRepository.deleteById(companyId);
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .companyId(company.getCompanyId())
                .name(company.getName())
                .category(company.getCategory())
                .website(company.getWebsite())
                .createdAt(company.getCreatedAt())
                .build();
    }
}
