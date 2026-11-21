package com.proveyu.enquiry.web;

import com.proveyu.enquiry.application.EnquiryService;
import com.proveyu.enquiry.domain.EnquiryModel.Enquiry;
import com.proveyu.enquiry.domain.EnquiryModel.EnquiryPage;
import com.proveyu.enquiry.domain.EnquiryModel.NewEnquiry;
import com.proveyu.enquiry.domain.EnquiryModel.Status;
import com.proveyu.enquiry.domain.EnquiryModel.StatusChange;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Pricing enquiries: the public form and the admin list. */
public final class EnquiryControllers {
    private EnquiryControllers() {}

    @Tag(name = "Public - Pricing enquiry")
    @RestController
    @RequestMapping("/api/v1/public/pricing-enquiries")
    @RequiredArgsConstructor
    public static class PublicEnquiryController {
        private final EnquiryService service;

        @PostMapping
        public ApiResponse<Void> submit(@Valid @RequestBody NewEnquiry r) {
            service.submit(r);
            return ApiResponse.success(null, "Thank you. Our team will contact you soon.");
        }
    }

    @Tag(name = "Admin - Pricing enquiries")
    @RestController
    @RequestMapping("/api/v1/admin/pricing-enquiries")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiredArgsConstructor
    public static class AdminEnquiryController {
        private final EnquiryService service;

        @GetMapping
        public ApiResponse<EnquiryPage> list(@RequestParam(required = false) Status status, @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
            return ApiResponse.success(service.list(status, q, page, size));
        }

        @GetMapping("/new-count")
        public ApiResponse<Map<String, Long>> newCount() {
            return ApiResponse.success(Map.of("new", service.newCount()));
        }

        @PostMapping("/{id}/status")
        public ApiResponse<Enquiry> status(Authentication auth, @PathVariable UUID id, @Valid @RequestBody StatusChange r) {
            return ApiResponse.success(service.setStatus(UUID.fromString(auth.getName()), id, r.status(), r.note()));
        }
    }
}
