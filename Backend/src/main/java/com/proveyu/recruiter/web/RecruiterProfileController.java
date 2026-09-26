package com.proveyu.recruiter.web;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.recruiter.domain.RecruiterProfile;
import com.proveyu.recruiter.domain.RecruiterProfileRepository;
import com.proveyu.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiters/profile")
@CrossOrigin(origins = "*")
public class RecruiterProfileController {

    private final UserRepository userRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;

    public RecruiterProfileController(UserRepository userRepository, RecruiterProfileRepository recruiterProfileRepository) {
        this.userRepository = userRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = userRepository.findById(userId).orElseThrow();
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findById(userId).orElse(new RecruiterProfile());

        Map<String, Object> profileData = new HashMap<>();
        profileData.put("contactName", user.getFullName());
        profileData.put("workEmail", user.getEmail());
        profileData.put("phone", user.getPhone());
        
        // From RecruiterProfile
        profileData.put("companyName", recruiterProfile.getCompanyName() != null ? recruiterProfile.getCompanyName() : "");
        profileData.put("city", recruiterProfile.getCity() != null ? recruiterProfile.getCity() : "");
        profileData.put("orgType", recruiterProfile.getOrgType() != null ? recruiterProfile.getOrgType() : "");
        profileData.put("website", recruiterProfile.getWebsite() != null ? recruiterProfile.getWebsite() : "");
        profileData.put("headline", recruiterProfile.getHeadline() != null ? recruiterProfile.getHeadline() : "");
        profileData.put("about", recruiterProfile.getAbout() != null ? recruiterProfile.getAbout() : "");
        profileData.put("avatar", recruiterProfile.getAvatar() != null ? recruiterProfile.getAvatar() : "");
        profileData.put("linkedin", recruiterProfile.getLinkedin() != null ? recruiterProfile.getLinkedin() : "");
        profileData.put("hiringVolume", recruiterProfile.getHiringVolume() != null ? recruiterProfile.getHiringVolume() : "");
        profileData.put("candidateLevel", recruiterProfile.getCandidateLevel() != null ? recruiterProfile.getCandidateLevel() : "");
        profileData.put("primaryTrack", recruiterProfile.getPrimaryTrack() != null ? recruiterProfile.getPrimaryTrack() : "");
        profileData.put("preferredCities", recruiterProfile.getPreferredCities() != null ? recruiterProfile.getPreferredCities() : "");
        profileData.put("customNotes", recruiterProfile.getCustomNotes() != null ? recruiterProfile.getCustomNotes() : "");

        return ResponseEntity.ok(ApiResponse.success(profileData, "Profile fetched"));
    }
    
    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(Authentication authentication, @RequestBody Map<String, Object> updates) {
        System.out.println("Received updates: " + updates);
        UUID userId = UUID.fromString(authentication.getName());
        User user = userRepository.findById(userId).orElseThrow();
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findById(userId).orElseGet(() -> {
            RecruiterProfile newProfile = new RecruiterProfile();
            newProfile.setUserId(userId);
            return newProfile;
        });
        
        if (updates.containsKey("contactName")) {
            user.setFullName((String) updates.get("contactName"));
        }
        if (updates.containsKey("workEmail")) {
            user.setEmail((String) updates.get("workEmail"));
        }
        if (updates.containsKey("phone")) {
            user.setPhone((String) updates.get("phone"));
        }
        
        if (updates.containsKey("companyName")) {
            recruiterProfile.setCompanyName((String) updates.get("companyName"));
        }
        if (updates.containsKey("city")) {
            recruiterProfile.setCity((String) updates.get("city"));
        }
        if (updates.containsKey("orgType")) {
            recruiterProfile.setOrgType((String) updates.get("orgType"));
        }
        if (updates.containsKey("website")) {
            recruiterProfile.setWebsite((String) updates.get("website"));
        }
        if (updates.containsKey("headline")) {
            recruiterProfile.setHeadline((String) updates.get("headline"));
        }
        if (updates.containsKey("about")) {
            recruiterProfile.setAbout((String) updates.get("about"));
        }
        if (updates.containsKey("avatar")) {
            recruiterProfile.setAvatar((String) updates.get("avatar"));
        }
        if (updates.containsKey("linkedin")) {
            recruiterProfile.setLinkedin((String) updates.get("linkedin"));
        }
        if (updates.containsKey("hiringVolume")) {
            recruiterProfile.setHiringVolume((String) updates.get("hiringVolume"));
        }
        if (updates.containsKey("candidateLevel")) {
            recruiterProfile.setCandidateLevel((String) updates.get("candidateLevel"));
        }
        if (updates.containsKey("primaryTrack")) {
            recruiterProfile.setPrimaryTrack((String) updates.get("primaryTrack"));
        }
        if (updates.containsKey("preferredCities")) {
            recruiterProfile.setPreferredCities((String) updates.get("preferredCities"));
        }
        if (updates.containsKey("customNotes")) {
            recruiterProfile.setCustomNotes((String) updates.get("customNotes"));
        }

        userRepository.save(user);
        recruiterProfileRepository.save(recruiterProfile);
        
        return ResponseEntity.ok(ApiResponse.success(updates, "Profile updated"));
    }
}
