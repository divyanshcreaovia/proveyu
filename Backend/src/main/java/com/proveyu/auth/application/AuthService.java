package com.proveyu.auth.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.auth.web.dto.AuthDto.*;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.security.JwtTokenProvider;
import com.proveyu.shared.security.PayloadDecryptionUtils;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PayloadDecryptionUtils payloadDecryptionUtils;
    private final EmailService emailService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DomainException("Email is already registered", HttpStatus.BAD_REQUEST, "EMAIL_EXISTS");
        }

        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new DomainException("Phone number is already registered", HttpStatus.BAD_REQUEST, "PHONE_EXISTS");
        }

        String rawPassword = payloadDecryptionUtils.decryptPassword(request.getPassword());

        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(request.getFullName())
                .role(request.getRole())
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name());

        log.info("[AUTH REGISTER] New user registered successfully: id=[{}] email=[{}] role=[{}]",
                savedUser.getId(), savedUser.getEmail(), savedUser.getRole());

        // Asynchronous Welcome Email
        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFullName());

        return AuthResponse.builder()
                .token(token)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new DomainException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        String rawPassword = payloadDecryptionUtils.decryptPassword(request.getPassword());

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        if (!user.isActive()) {
            throw new DomainException("Account is disabled", HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        log.info("[AUTH LOGIN] User logged in successfully: id=[{}] email=[{}]", user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new DomainException("No account found with this email address", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        // Generate secure 6-digit OTP reset token
        String resetToken = String.format("%06d", (int) (Math.random() * 900000) + 100000);
        user.setResetToken(resetToken);
        user.setResetTokenExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));

        userRepository.save(user);

        log.info("[AUTH FORGOT_PASSWORD] Generated password reset token for email=[{}]: token=[{}]", request.getEmail(), resetToken);

        // Asynchronous OTP Email
        emailService.sendPasswordResetOtpEmail(user.getEmail(), resetToken);

        return new ForgotPasswordResponse(
                user.getEmail(),
                resetToken,
                "Password reset token generated. Valid for 15 minutes."
        );
    }

    @Transactional(readOnly = true)
    public boolean verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new DomainException("No account found with this email address", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        if (user.getResetToken() == null || !user.getResetToken().equals(request.getOtp())) {
            throw new DomainException("Invalid OTP token", HttpStatus.BAD_REQUEST, "INVALID_OTP");
        }

        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("OTP token has expired", HttpStatus.BAD_REQUEST, "OTP_EXPIRED");
        }

        return true;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.getResetToken())
                .orElseThrow(() -> new DomainException("Invalid or expired password reset token", HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN"));

        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("Password reset token has expired", HttpStatus.BAD_REQUEST, "RESET_TOKEN_EXPIRED");
        }

        String newRawPassword = payloadDecryptionUtils.decryptPassword(request.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);

        userRepository.save(user);

        log.info("[AUTH RESET_PASSWORD] Password updated successfully for user id=[{}] email=[{}]", user.getId(), user.getEmail());
    }

    public void logout(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                Date expirationDate = jwtTokenProvider.getExpirationDateFromToken(token);
                tokenBlacklistService.blacklistToken(token, expirationDate.getTime());
                log.info("[AUTH LOGOUT] Token successfully blacklisted and user logged out.");
            }
        }
    }
}
