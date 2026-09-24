package com.proveyu.verification.application;

import com.proveyu.shared.error.DomainException;
import com.proveyu.verification.domain.Company;
import com.proveyu.verification.domain.Company.GstStatus;
import com.proveyu.verification.domain.Company.VerificationStatus;
import com.proveyu.verification.infrastructure.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GstVerificationService {

    private final CompanyRepository companyRepository;
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    @Transactional
    public Company verifyCompanyGst(String legalName, String tradeName, String gstin, String address, String state, String domain) {
        String cleanGstin = gstin.trim().toUpperCase();
        if (!GSTIN_PATTERN.matcher(cleanGstin).matches()) {
            throw new DomainException("Invalid GSTIN format. GSTIN must be 15 alphanumeric characters.", HttpStatus.BAD_REQUEST, "INVALID_GSTIN_FORMAT");
        }

        Company company = companyRepository.findByGstin(cleanGstin).orElse(
                Company.builder()
                        .gstin(cleanGstin)
                        .legalName(legalName)
                        .tradeName(tradeName)
                        .registeredAddress(address)
                        .state(state)
                        .websiteDomain(domain)
                        .gstStatus(GstStatus.ACTIVE)
                        .verificationStatus(VerificationStatus.PENDING)
                        .build()
        );

        company.setLegalName(legalName);
        company.setTradeName(tradeName);
        company.setRegisteredAddress(address);
        company.setState(state);
        company.setWebsiteDomain(domain);
        company.setGstStatus(GstStatus.ACTIVE);
        company.setVerificationStatus(VerificationStatus.VERIFIED);
        company.setVerifiedAt(Instant.now());

        return companyRepository.save(company);
    }
}
