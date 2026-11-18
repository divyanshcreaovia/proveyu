package com.proveyu.student.web;

import com.proveyu.auth.web.dto.AuthDto.ForgotPasswordResponse;
import com.proveyu.shared.response.ApiResponse;
import com.proveyu.student.application.StudentPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public student account activation. The URL stays under /college/auth, where the frontend and the security rules expect it. */
@Tag(name = "Student - Account activation")
@RestController
@RequestMapping("/api/v1/college/auth")
@RequiredArgsConstructor
public class StudentAuthController {

    private final StudentPortalService studentPortalService;

    public record StudentActivationRequest(@NotBlank @Email String email, @NotBlank @Size(max = 50) String rollNumber) {}

    @Operation(summary = "Student sign-up (public): matches e-mail + roll number to a college student record and e-mails a 6-digit code. "
            + "The student then sets a password with POST /api/v1/auth/reset-password.")
    @PostMapping("/student-signup")
    public ApiResponse<ForgotPasswordResponse> studentSignup(@Valid @RequestBody StudentActivationRequest request) {
        var r = studentPortalService.startActivation(request.email(), request.rollNumber());
        return ApiResponse.success(r, r.getMessage());
    }
}
