package com.proveyu.auth.web.dto;

import com.proveyu.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class AuthDto {

    public static class RegisterRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        private String phone;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotNull(message = "Role is required")
        private Role role;

        public RegisterRequest() {}

        public RegisterRequest(String email, String phone, String password, String fullName, Role role) {
            this.email = email;
            this.phone = phone;
            this.password = password;
            this.fullName = fullName;
            this.role = role;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public Role getRole() { return role; }
        public void setRole(Role role) { this.role = role; }
    }

    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;

        public LoginRequest() {}

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class AuthResponse {
        private String token;
        private UUID userId;
        private String email;
        private String fullName;
        private Role role;

        public AuthResponse() {}

        public AuthResponse(String token, UUID userId, String email, String fullName, Role role) {
            this.token = token;
            this.userId = userId;
            this.email = email;
            this.fullName = fullName;
            this.role = role;
        }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public Role getRole() { return role; }
        public void setRole(Role role) { this.role = role; }

        public static AuthResponseBuilder builder() {
            return new AuthResponseBuilder();
        }

        public static class AuthResponseBuilder {
            private String token;
            private UUID userId;
            private String email;
            private String fullName;
            private Role role;

            public AuthResponseBuilder token(String token) { this.token = token; return this; }
            public AuthResponseBuilder userId(UUID userId) { this.userId = userId; return this; }
            public AuthResponseBuilder email(String email) { this.email = email; return this; }
            public AuthResponseBuilder fullName(String fullName) { this.fullName = fullName; return this; }
            public AuthResponseBuilder role(Role role) { this.role = role; return this; }

            public AuthResponse build() {
                return new AuthResponse(token, userId, email, fullName, role);
            }
        }
    }

    public static class ForgotPasswordRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        public ForgotPasswordRequest() {}
        public ForgotPasswordRequest(String email) { this.email = email; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ForgotPasswordResponse {
        private String email;
        private String resetToken;
        private String message;

        public ForgotPasswordResponse() {}
        public ForgotPasswordResponse(String email, String resetToken, String message) {
            this.email = email;
            this.resetToken = resetToken;
            this.message = message;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getResetToken() { return resetToken; }
        public void setResetToken(String resetToken) { this.resetToken = resetToken; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class VerifyOtpRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "OTP reset token is required")
        private String otp;

        public VerifyOtpRequest() {}
        public VerifyOtpRequest(String email, String otp) {
            this.email = email;
            this.otp = otp;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getOtp() { return otp; }
        public void setOtp(String otp) { this.otp = otp; }
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "Reset token is required")
        private String resetToken;

        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "New password must be at least 6 characters")
        private String newPassword;

        public ResetPasswordRequest() {}
        public ResetPasswordRequest(String resetToken, String newPassword) {
            this.resetToken = resetToken;
            this.newPassword = newPassword;
        }

        public String getResetToken() { return resetToken; }
        public void setResetToken(String resetToken) { this.resetToken = resetToken; }

        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
