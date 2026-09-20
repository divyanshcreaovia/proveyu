package com.proveyu.candidate.web;

import com.proveyu.candidate.application.CandidateProfileService;
import com.proveyu.candidate.web.dto.CandidatePassportDto.PassportResponse;
import com.proveyu.candidate.web.dto.CandidateProfileDto.ProfileResponse;
import com.proveyu.candidate.web.dto.CandidateProfileDto.UpsertProfileRequest;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> upsertProfile(
            Authentication authentication,
            @Valid @RequestBody UpsertProfileRequest request) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                candidateProfileService.upsertProfile(candidateId, request),
                "Candidate profile updated successfully"
        ));
    }

    @PostMapping("/resume/upload")
    public ResponseEntity<ApiResponse<ProfileResponse>> uploadResume(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        UUID candidateId = UUID.fromString(authentication.getName());
        ProfileResponse response = candidateProfileService.uploadResume(candidateId, file);
        return ResponseEntity.ok(ApiResponse.success(response, "PDF Resume uploaded and linked successfully"));
    }

    @GetMapping("/resume/download/{filename:.+}")
    public ResponseEntity<Resource> downloadResume(@PathVariable String filename) {
        Resource resource = candidateProfileService.loadResumeResource(filename);
        String contentType = "application/pdf";
        if (filename.endsWith(".doc")) {
            contentType = "application/msword";
        } else if (filename.endsWith(".docx")) {
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(Authentication authentication) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                candidateProfileService.getProfile(candidateId),
                "Candidate profile retrieved successfully"
        ));
    }

    @GetMapping("/passport")
    public ResponseEntity<ApiResponse<PassportResponse>> getPassport(Authentication authentication) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                candidateProfileService.getPassport(candidateId),
                "Candidate score passport retrieved successfully"
        ));
    }
}
