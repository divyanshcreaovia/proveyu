package com.proveyu.college.web;

import com.proveyu.college.application.CompanyService;
import com.proveyu.college.application.TabularExporter;
import com.proveyu.college.application.dto.CompanyDtos.*;
import com.proveyu.college.domain.CollegeEnums.MouStatus;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "College - Companies")
@RestController
@RequestMapping("/api/v1/college/companies")
@RequiredArgsConstructor
public class CollegeCompanyController {

    private final CompanyService companyService;
    private final TabularExporter exporter;

    @GetMapping
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PageResponse<CompanyResponse>> list(CollegeContext ctx, @RequestParam(required = false) String q,
            @RequestParam(required = false) String industry, @RequestParam(required = false) MouStatus mouStatus,
            @RequestParam(defaultValue = "false") boolean deleted, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "name") String sort, @RequestParam(defaultValue = "asc") String dir) {
        return ApiResponse.success(companyService.page(ctx, q, industry, mouStatus, deleted, page, size, sort, dir));
    }

    @GetMapping("/export")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<byte[]> export(CollegeContext ctx, @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String q, @RequestParam(required = false) String industry, @RequestParam(required = false) MouStatus mouStatus) {
        List<List<Object>> rows = new ArrayList<>();
        companyService.all(ctx, q, industry, mouStatus).forEach(c -> rows.add(List.of(c.name(), s(c.industry()), s(c.hqLocation()), c.mouStatus().name(),
                c.drivesCount(), c.offersCount(), c.avgRating() == null ? "" : c.avgRating(), s(c.primaryContactName()), s(c.primaryContactEmail()))));
        return exporter.export(new TabularExporter.Table("Companies", List.of("Company", "Industry", "Location", "MoU", "Drives", "Offers",
                "Rating", "Primary contact", "Contact e-mail"), rows), format, "companies").toResponse();
    }

    private static String s(String v) {
        return v == null ? "" : v;
    }

    @GetMapping("/recruiters")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<List<RecruiterOption>> recruiters(@RequestParam String q) {
        return ApiResponse.success(companyService.searchRecruiters(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<CompanyDetail> get(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(companyService.detail(ctx, id));
    }

    @PostMapping
    @PreAuthorize(CollegeAccess.WRITE)
    public ResponseEntity<ApiResponse<CompanyResponse>> create(CollegeContext ctx, @Valid @RequestBody CompanyRequest r) {
        return ResponseEntity.status(201).body(ApiResponse.success(companyService.create(ctx, r), "Company added"));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<CompanyResponse> update(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody CompanyRequest r) {
        return ApiResponse.success(companyService.update(ctx, id, r), "Company updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> delete(CollegeContext ctx, @PathVariable UUID id) {
        companyService.softDelete(ctx, id);
        return ApiResponse.success(null, "Company moved to trash");
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<CompanyResponse> restore(CollegeContext ctx, @PathVariable UUID id) {
        return ApiResponse.success(companyService.restore(ctx, id), "Company restored");
    }

    @PostMapping("/{id}/contacts")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<ContactResponse> addContact(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody ContactRequest r) {
        return ApiResponse.success(companyService.addContact(ctx, id, r), "Contact added");
    }

    @PutMapping("/contacts/{contactId}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<ContactResponse> updateContact(CollegeContext ctx, @PathVariable UUID contactId, @Valid @RequestBody ContactRequest r) {
        return ApiResponse.success(companyService.updateContact(ctx, contactId, r), "Contact updated");
    }

    @DeleteMapping("/contacts/{contactId}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> deleteContact(CollegeContext ctx, @PathVariable UUID contactId) {
        companyService.deleteContact(ctx, contactId);
        return ApiResponse.success(null, "Contact removed");
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<FeedbackResponse> feedback(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody FeedbackRequest r) {
        return ApiResponse.success(companyService.addFeedback(ctx, id, r), "Feedback saved");
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<Void> invite(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody InviteCompanyRequest r) {
        companyService.invite(ctx, id, r);
        return ApiResponse.success(null, "Invitation sent");
    }
}
