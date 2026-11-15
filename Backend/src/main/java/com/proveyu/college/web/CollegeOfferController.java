package com.proveyu.college.web;

import com.proveyu.college.application.CollegeDirectory;
import com.proveyu.college.application.CollegeFileService;
import com.proveyu.college.application.OfferService;
import com.proveyu.college.application.StudentService;
import com.proveyu.college.application.TabularExporter;
import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.domain.CollegeEnums.OfferStatus;
import com.proveyu.college.domain.CollegeFile;
import com.proveyu.college.domain.CollegeOffer;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "College - Offers")
@RestController
@RequestMapping("/api/v1/college/offers")
@RequiredArgsConstructor
public class CollegeOfferController {

    private final OfferService offerService;
    private final StudentService studentService;
    private final CollegeDirectory directory;
    private final CollegeFileService fileService;
    private final TabularExporter exporter;

    @GetMapping
    @PreAuthorize(CollegeAccess.READ)
    public ApiResponse<PageResponse<OfferResponse>> list(CollegeContext ctx, @RequestParam(required = false) OfferStatus status,
            @RequestParam(required = false) UUID companyId, @RequestParam(required = false) UUID driveId,
            @RequestParam(required = false) Integer batchYear, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(offerService.page(ctx, status, companyId, driveId, batchYear, q, page, size));
    }

    @GetMapping("/export")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<byte[]> export(CollegeContext ctx, @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) OfferStatus status, @RequestParam(required = false) Integer batchYear) {
        List<List<Object>> rows = new ArrayList<>();
        offerService.all(ctx, status, null, null, batchYear, null).forEach(o -> rows.add(List.of(o.rollNumber(), o.studentName(), s(o.branchCode()),
                o.batchYear(), s(o.companyName()), o.roleTitle(), o.ctcLpa(), s(o.tierName()), o.status().name(),
                o.offerDate() == null ? "" : o.offerDate().toString(), o.joiningDate() == null ? "" : o.joiningDate().toString())));
        return exporter.export(new TabularExporter.Table("Offers", List.of("Roll No", "Student", "Branch", "Batch", "Company", "Role",
                "CTC (LPA)", "Tier", "Status", "Offer date", "Joining date"), rows), format, "offers").toResponse();
    }

    private static String s(String v) { return v == null ? "" : v; }

    @PostMapping
    @PreAuthorize(CollegeAccess.WRITE)
    public ResponseEntity<ApiResponse<OfferResponse>> create(CollegeContext ctx, @Valid @RequestBody OfferRequest r) {
        return ResponseEntity.status(201).body(ApiResponse.success(offerService.create(ctx, r), "Offer recorded"));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<OfferResponse> update(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody OfferRequest r) {
        return ApiResponse.success(offerService.update(ctx, id, r), "Offer updated");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<OfferResponse> status(CollegeContext ctx, @PathVariable UUID id, @Valid @RequestBody OfferStatusRequest r) {
        return ApiResponse.success(offerService.changeStatus(ctx, id, r), "Offer is now " + r.status());
    }

    @PostMapping(value = "/{id}/letter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CollegeAccess.WRITE)
    public ApiResponse<OfferResponse> uploadLetter(CollegeContext ctx, @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(offerService.uploadLetter(ctx, id, file), "Offer letter uploaded");
    }

    @GetMapping("/{id}/letter")
    @PreAuthorize(CollegeAccess.READ)
    public ResponseEntity<Resource> letter(CollegeContext ctx, @PathVariable UUID id) {
        CollegeOffer o = directory.offer(ctx.collegeId(), id);
        studentService.scoped(ctx, o.getStudentId());
        CollegeFile f = offerService.letter(ctx.collegeId(), o);
        return Downloads.inline(fileService.load(f), f);
    }
}
