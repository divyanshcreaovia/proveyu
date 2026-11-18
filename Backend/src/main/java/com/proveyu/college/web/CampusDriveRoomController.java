package com.proveyu.college.web;

import com.proveyu.college.application.DriveRoomService;
import com.proveyu.college.application.dto.DriveDtos.*;
import com.proveyu.college.application.dto.DriveRoomDtos.*;
import com.proveyu.college.domain.CollegeFile;
import com.proveyu.college.application.CollegeFileService;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Company side of the Drive Room: a RECRUITER works only on drives a college invited them to. */
@Tag(name = "Recruiter - Campus Drive Room")
@RestController
@RequestMapping("/api/v1/recruiter/campus")
@PreAuthorize("hasRole('RECRUITER')")
@RequiredArgsConstructor
public class CampusDriveRoomController {

    private final DriveRoomService service;
    private final CollegeFileService fileService;

    private static UUID uid(Authentication a) {
        return UUID.fromString(a.getName());
    }

    @GetMapping("/drives")
    public ApiResponse<List<RoomDriveCard>> drives(Authentication auth) {
        return ApiResponse.success(service.myDrives(uid(auth)));
    }

    @GetMapping("/drives/{driveId}")
    public ApiResponse<RoomSummary> summary(Authentication auth, @PathVariable UUID driveId) {
        return ApiResponse.success(service.summary(uid(auth), driveId));
    }

    @GetMapping("/drives/{driveId}/applicants")
    public ApiResponse<List<RoomApplicant>> applicants(Authentication auth, @PathVariable UUID driveId) {
        return ApiResponse.success(service.applicants(uid(auth), driveId));
    }

    @GetMapping("/drives/{driveId}/applicants/{applicationId}/resume")
    public ResponseEntity<Resource> resume(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID applicationId) {
        CollegeFile f = service.resumeFile(uid(auth), driveId, applicationId);
        String name = f.getOriginalName().replaceAll("[\"\\r\\n]", "_");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"").header("X-Content-Type-Options", "nosniff")
                .body(fileService.load(f));
    }

    @PostMapping("/drives/{driveId}/resumes.zip")
    public ResponseEntity<byte[]> resumes(Authentication auth, @PathVariable UUID driveId, @RequestBody(required = false) List<UUID> applicationIds) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resumes.zip\"").body(service.resumesZip(uid(auth), driveId, applicationIds));
    }

    @PatchMapping("/drives/{driveId}/applications/status")
    public ApiResponse<BulkResult> status(Authentication auth, @PathVariable UUID driveId, @Valid @RequestBody RoomStatusRequest body) {
        return ApiResponse.success(service.setStatus(uid(auth), driveId, body), "Applications updated");
    }

    @PostMapping("/drives/{driveId}/rounds/{roundId}/start")
    public ApiResponse<RoundResponse> start(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID roundId) {
        return ApiResponse.success(service.startRound(uid(auth), driveId, roundId), "Round started");
    }

    @PostMapping("/drives/{driveId}/rounds/{roundId}/complete")
    public ApiResponse<RoundResponse> complete(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID roundId) {
        return ApiResponse.success(service.completeRound(uid(auth), driveId, roundId), "Round completed");
    }

    @GetMapping("/drives/{driveId}/rounds/{roundId}/results")
    public ApiResponse<List<RoundResultRow>> results(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID roundId) {
        return ApiResponse.success(service.roundResults(uid(auth), driveId, roundId));
    }

    @PutMapping("/drives/{driveId}/rounds/{roundId}/results")
    public ApiResponse<List<RoundResultRow>> saveResults(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID roundId,
                                                         @Valid @RequestBody ResultsRequest body) {
        return ApiResponse.success(service.saveResults(uid(auth), driveId, roundId, body), "Results saved");
    }

    @GetMapping("/drives/{driveId}/rounds/{roundId}/scorecards")
    public ApiResponse<List<ScorecardResponse>> scorecards(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID roundId) {
        return ApiResponse.success(service.scorecards(uid(auth), driveId, roundId));
    }

    @PutMapping("/drives/{driveId}/rounds/{roundId}/scorecards/{applicationId}")
    public ApiResponse<ScorecardResponse> saveScorecard(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID roundId,
                                                        @PathVariable UUID applicationId, @Valid @RequestBody ScorecardRequest body) {
        return ApiResponse.success(service.saveScorecard(uid(auth), driveId, roundId, applicationId, body), "Scorecard saved");
    }

    @GetMapping("/drives/{driveId}/offers")
    public ApiResponse<List<OfferResponse>> offers(Authentication auth, @PathVariable UUID driveId) {
        return ApiResponse.success(service.offers(uid(auth), driveId));
    }

    @PostMapping("/drives/{driveId}/offers")
    public ApiResponse<RoomOfferResult> createOffers(Authentication auth, @PathVariable UUID driveId, @Valid @RequestBody RoomOfferRequest body) {
        RoomOfferResult r = service.createOffers(uid(auth), driveId, body);
        return ApiResponse.success(r, r.created() + " offer(s) recorded");
    }

    @PostMapping(value = "/drives/{driveId}/offers/{offerId}/letter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OfferResponse> letter(Authentication auth, @PathVariable UUID driveId, @PathVariable UUID offerId,
                                             @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.uploadLetter(uid(auth), driveId, offerId, file), "Offer letter uploaded");
    }

    @GetMapping("/drives/{driveId}/report")
    public ResponseEntity<byte[]> report(Authentication auth, @PathVariable UUID driveId, @RequestParam(defaultValue = "xlsx") String format) {
        return service.report(uid(auth), driveId, format).toResponse();
    }
}
