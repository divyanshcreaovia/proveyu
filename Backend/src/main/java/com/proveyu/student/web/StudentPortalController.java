package com.proveyu.student.web;

import com.proveyu.college.web.Downloads;
import com.proveyu.student.application.StudentPortalService;
import com.proveyu.student.application.StudentPortalService.*;
import com.proveyu.college.application.dto.DriveDtos.OfferResponse;
import com.proveyu.college.application.dto.StudentDtos.DocumentResponse;
import com.proveyu.college.application.CollegeFileService;
import com.proveyu.college.domain.CollegeEnums.DocType;
import com.proveyu.college.domain.CollegeFile;
import com.proveyu.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import com.proveyu.college.security.CollegeAccess;
import com.proveyu.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Students (CANDIDATE role) see only their own college's drives, applications and offers. */
@Tag(name = "Student - Campus drives")
@RestController
@RequestMapping("/api/v1/college/student")
@RequiredArgsConstructor
public class StudentPortalController {

    private final StudentPortalService portalService;
    private final CollegeFileService fileService;

    private static UUID uid(Authentication a) {
        return UUID.fromString(a.getName());
    }

    @GetMapping("/profile")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<PortalProfile> profile(Authentication auth) {
        return ApiResponse.success(portalService.profile(uid(auth)));
    }

    @PatchMapping("/profile")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<PortalProfile> updateProfile(Authentication auth, @Valid @RequestBody ProfileUpdate body) {
        return ApiResponse.success(portalService.updateProfile(uid(auth), body), "Profile updated");
    }

    @GetMapping("/drives")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<List<PortalDrive>> drives(Authentication auth) {
        return ApiResponse.success(portalService.drives(uid(auth)));
    }

    @PostMapping("/drives/{driveId}/apply")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<PortalApplication> apply(Authentication auth, @PathVariable UUID driveId) {
        return ApiResponse.success(portalService.apply(uid(auth), driveId), "Application submitted");
    }

    @GetMapping("/applications")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<List<PortalApplication>> applications(Authentication auth) {
        return ApiResponse.success(portalService.applications(uid(auth)));
    }

    @PostMapping("/applications/{id}/withdraw")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<PortalApplication> withdraw(Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(portalService.withdraw(uid(auth), id), "Application withdrawn");
    }

    @GetMapping("/offers")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<List<OfferResponse>> offers(Authentication auth) {
        return ApiResponse.success(portalService.offers(uid(auth)));
    }

    @PostMapping("/offers/{id}/respond")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<OfferResponse> respond(Authentication auth, @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        if (body.get("accept") == null) {
            throw new com.proveyu.shared.error.DomainException("Send accept: true or false", org.springframework.http.HttpStatus.BAD_REQUEST, "ACCEPT_REQUIRED");
        }
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        return ApiResponse.success(portalService.respond(uid(auth), id, accept), accept ? "Offer accepted" : "Offer declined");
    }

    @GetMapping("/documents")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<List<DocumentResponse>> documents(Authentication auth) {
        return ApiResponse.success(portalService.documents(uid(auth)));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<DocumentResponse> upload(Authentication auth, @RequestParam DocType docType, @RequestParam(required = false) String label,
                                                @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(portalService.upload(uid(auth), docType, label, file), "Document saved");
    }

    @GetMapping("/documents/{docId}/file")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ResponseEntity<Resource> documentFile(Authentication auth, @PathVariable UUID docId) {
        CollegeFile f = portalService.documentFile(uid(auth), docId);
        return Downloads.inline(fileService.load(f), f);
    }

    @DeleteMapping("/documents/{docId}")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<Void> deleteDocument(Authentication auth, @PathVariable UUID docId) {
        portalService.deleteDocument(uid(auth), docId);
        return ApiResponse.success(null, "Document deleted");
    }

    @GetMapping("/offers/{id}/letter")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ResponseEntity<Resource> offerLetter(Authentication auth, @PathVariable UUID id) {
        CollegeFile f = portalService.offerLetter(uid(auth), id);
        return Downloads.inline(fileService.load(f), f);
    }

    @GetMapping("/applications/{id}/rounds")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<List<PortalRound>> rounds(Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(portalService.rounds(uid(auth), id));
    }

    @GetMapping("/interviews")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<List<PortalInterview>> interviews(Authentication auth) {
        return ApiResponse.success(portalService.interviews(uid(auth)));
    }

    @GetMapping("/announcements")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<PageResponse<PortalAnnouncement>> announcements(Authentication auth, @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(portalService.announcements(uid(auth), page, size));
    }

    @PostMapping("/announcements/{id}/read")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<Void> markRead(Authentication auth, @PathVariable UUID id) {
        portalService.markAnnouncementRead(uid(auth), id);
        return ApiResponse.success(null, "Marked as read");
    }

    @PostMapping("/announcements/read-all")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<Void> markAllRead(Authentication auth) {
        portalService.markAllAnnouncementsRead(uid(auth));
        return ApiResponse.success(null, "All marked as read");
    }

    @GetMapping("/chat/contacts")
    @PreAuthorize(CollegeAccess.STUDENT)
    public ApiResponse<List<PortalContact>> contacts(Authentication auth) {
        return ApiResponse.success(portalService.contacts(uid(auth)));
    }
}
