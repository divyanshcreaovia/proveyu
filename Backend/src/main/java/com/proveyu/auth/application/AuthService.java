package com.proveyu.auth.application;

import com.proveyu.auth.domain.EmailVerificationToken;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.EmailVerificationTokenRepository;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.auth.web.dto.AuthDto.*;
import com.proveyu.college.domain.CollegeEnums.MemberStatus;
import com.proveyu.college.domain.CollegeMember;
import com.proveyu.college.infrastructure.CollegeMemberRepository;
import com.proveyu.college.infrastructure.CollegeProfileRepository;
import com.proveyu.cxo.domain.CxoMember;
import com.proveyu.cxo.infrastructure.CxoMemberRepository;
import com.proveyu.shared.config.AppProperties;
import com.proveyu.shared.email.EmailService;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.security.JwtTokenProvider;
import com.proveyu.shared.security.PayloadDecryptionUtils;
import com.proveyu.shared.security.TokenBlacklistService;
import com.proveyu.shared.util.TokenUtils;
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
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final Set<Role> COLLEGE_ROLES = EnumSet.of(Role.COLLEGE_ADMIN, Role.COLLEGE_COORDINATOR, Role.COLLEGE_VIEWER);
    private static final Set<Role> SELF_REGISTER_ROLES = EnumSet.of(Role.CANDIDATE, Role.RECRUITER);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.proveyu.shared.security.UserBlockList userBlockList;
    private final PayloadDecryptionUtils payloadDecryptionUtils;
    private final EmailService emailService;
    private final TokenBlacklistService tokenBlacklistService;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final CollegeMemberRepository collegeMemberRepository;
    private final CollegeProfileRepository collegeProfileRepository;
    private final CxoMemberRepository cxoMemberRepository;
    private final AppProperties appProperties;

    /** OTP brute-force guard (per e-mail, in memory). */
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> otpFailures = new java.util.concurrent.ConcurrentHashMap<>();

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!SELF_REGISTER_ROLES.contains(request.getRole())) {
            throw new DomainException("Self-registration is only available for candidates and recruiters. Colleges register via the college portal.",
                    HttpStatus.BAD_REQUEST, "ROLE_NOT_ALLOWED");
        }
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email is already registered", HttpStatus.BAD_REQUEST, "EMAIL_EXISTS");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new DomainException("Phone number is already registered", HttpStatus.BAD_REQUEST, "PHONE_EXISTS");
        }

        String rawPassword = payloadDecryptionUtils.decryptPassword(request.getPassword());
        User savedUser = userRepository.save(User.builder()
                .email(email)
                .phone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(request.getFullName())
                .role(request.getRole())
                .active(true)
                .build());
        String token = jwtTokenProvider.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name());
        log.info("[AUTH REGISTER] New user registered: id=[{}] role=[{}]", savedUser.getId(), savedUser.getRole());
        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFullName());

        return AuthResponse.builder().token(token).userId(savedUser.getId()).email(savedUser.getEmail())
                .fullName(savedUser.getFullName()).role(savedUser.getRole()).emailVerified(savedUser.getEmailVerifiedAt() != null).build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .or(() -> userRepository.findByEmail(request.getEmail()))
                .orElseThrow(() -> new DomainException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        String rawPassword = payloadDecryptionUtils.decryptPassword(request.getPassword());
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        if (!user.isActive()) {
            throw new DomainException("Account is disabled", HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }

        AuthResponse.AuthResponseBuilder response = AuthResponse.builder()
                .userId(user.getId()).email(user.getEmail()).fullName(user.getFullName()).role(user.getRole())
                .emailVerified(user.getEmailVerifiedAt() != null);

        if (COLLEGE_ROLES.contains(user.getRole())) {
            if (user.getEmailVerifiedAt() == null) {
                throw new DomainException("Please verify your e-mail address before signing in", HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
            }
            CollegeMember member = collegeMemberRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new DomainException("No college membership found for this account", HttpStatus.FORBIDDEN, "NOT_COLLEGE_MEMBER"));
            if (member.getStatus() != MemberStatus.ACTIVE) {
                throw new DomainException("Your college access has been revoked", HttpStatus.FORBIDDEN, "ACCESS_REVOKED");
            }
            member.setLastLoginAt(Instant.now());
            collegeMemberRepository.save(member);
            collegeProfileRepository.findById(member.getCollegeId()).ifPresent(p -> response
                    .collegeId(p.getCollegeId()).collegeName(p.getCollegeName()).collegeApprovalStatus(p.getApprovalStatus().name()));
        }
        if (user.getRole() == Role.CXO) {
            CxoMember cxo = cxoMemberRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new DomainException("This leadership account is not linked to a college", HttpStatus.FORBIDDEN, "NOT_CXO_MEMBER"));
            if (cxo.getStatus() != MemberStatus.ACTIVE) {
                throw new DomainException("Your leadership access has been revoked", HttpStatus.FORBIDDEN, "ACCESS_REVOKED");
            }
            cxo.setLastLoginAt(Instant.now());
            cxoMemberRepository.save(cxo);
            collegeProfileRepository.findById(cxo.getCollegeId()).ifPresent(p -> response
                    .collegeId(p.getCollegeId()).collegeName(p.getCollegeName()).collegeApprovalStatus(p.getApprovalStatus().name()));
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        log.info("[AUTH LOGIN] User logged in: id=[{}]", user.getId());
        return response.token(token).build();
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        String generic = "If an account exists for this e-mail, a 6-digit code has been sent. It is valid for 15 minutes.";
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            // Do not reveal whether the account exists.
            return new ForgotPasswordResponse(email, null, generic);
        }
        User user = found.get();
        String otp = TokenUtils.randomOtp();
        user.setResetToken(TokenUtils.sha256(otp));
        user.setResetTokenExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        userRepository.save(user);
        otpFailures.remove(email);
        emailService.sendPasswordResetOtpEmail(user.getEmail(), otp);

        if (appProperties.isExposeTokens()) {
            log.warn("[DEV ONLY] Password reset OTP for {} is {}", email, otp);
            return new ForgotPasswordResponse(email, otp, generic);
        }
        log.info("[AUTH FORGOT_PASSWORD] Reset code issued for user id=[{}]", user.getId());
        return new ForgotPasswordResponse(email, null, generic);
    }

    private User checkOtp(String rawEmail, String otp) {
        String email = normalizeEmail(rawEmail);
        if (otpFailures.getOrDefault(email, 0) >= MAX_OTP_ATTEMPTS) {
            throw new DomainException("Too many invalid attempts. Request a new code.", HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException("Invalid or expired code", HttpStatus.BAD_REQUEST, "INVALID_OTP"));
        if (user.getResetToken() == null || otp == null || !TokenUtils.safeEquals(user.getResetToken(), TokenUtils.sha256(otp.trim()))) {
            otpFailures.merge(email, 1, Integer::sum);
            throw new DomainException("Invalid or expired code", HttpStatus.BAD_REQUEST, "INVALID_OTP");
        }
        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("Code has expired. Request a new one.", HttpStatus.BAD_REQUEST, "OTP_EXPIRED");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public boolean verifyOtp(VerifyOtpRequest request) {
        checkOtp(request.getEmail(), request.getOtp());
        return true;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = checkOtp(request.getEmail(), request.getResetToken());
        String newRawPassword = payloadDecryptionUtils.decryptPassword(request.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        // Completing an OTP reset proves mailbox ownership.
        if (user.getEmailVerifiedAt() == null) user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
        userBlockList.passwordChanged(user.getId()); // anyone still signed in with the old password is signed out
        otpFailures.remove(normalizeEmail(request.getEmail()));
        log.info("[AUTH RESET_PASSWORD] Password updated for user id=[{}]", user.getId());
    }

    /** Creates a single-use e-mail verification token, mails the link and returns the raw token (for dev exposure only). */
    @Transactional
    public String issueEmailVerification(User user) {
        String raw = TokenUtils.randomUrlToken();
        verificationTokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getId()).tokenHash(TokenUtils.sha256(raw)).expiresAt(Instant.now().plus(48, ChronoUnit.HOURS)).build());
        String link = appProperties.getFrontendUrl() + "/college/verify-email?token=" + raw;
        emailService.sendHtml(user.getEmail(), "Verify your ProveYu account",
                "<p>Hi " + org.springframework.web.util.HtmlUtils.htmlEscape(user.getFullName()) + ",</p>"
                        + "<p>Please confirm your e-mail address to activate your college account:</p>"
                        + "<p><a href=\"" + link + "\">Verify e-mail</a></p><p>The link expires in 48 hours.</p>");
        if (appProperties.isExposeTokens()) log.warn("[DEV ONLY] Verification link for {}: {}", user.getEmail(), link);
        return raw;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken t = verificationTokenRepository.findByTokenHash(TokenUtils.sha256(rawToken))
                .orElseThrow(() -> new DomainException("Invalid verification link", HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
        if (t.getUsedAt() != null) {
            throw new DomainException("This link has already been used", HttpStatus.BAD_REQUEST, "TOKEN_USED");
        }
        if (t.getExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("Verification link has expired. Request a new one.", HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }
        User user = userRepository.findById(t.getUserId())
                .orElseThrow(() -> new DomainException("Account not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
        t.setUsedAt(Instant.now());
        verificationTokenRepository.save(t);
    }

    /** Always answers the same way so it cannot be used to enumerate accounts. Returns dev token when exposed. */
    @Transactional
    public String resendVerification(String rawEmail) {
        return userRepository.findByEmail(normalizeEmail(rawEmail))
                .filter(u -> u.getEmailVerifiedAt() == null)
                .map(this::issueEmailVerification)
                .filter(t -> appProperties.isExposeTokens())
                .orElse(null);
    }

    public AuthResponse issueSession(User user) {
        AuthResponse.AuthResponseBuilder b = AuthResponse.builder()
                .token(jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name()))
                .userId(user.getId()).email(user.getEmail()).fullName(user.getFullName()).role(user.getRole())
                .emailVerified(user.getEmailVerifiedAt() != null);
        collegeMemberRepository.findByUserId(user.getId())
                .flatMap(m -> collegeProfileRepository.findById(m.getCollegeId()))
                .ifPresent(p -> b.collegeId(p.getCollegeId()).collegeName(p.getCollegeName()).collegeApprovalStatus(p.getApprovalStatus().name()));
        return b.build();
    }

    public void logout(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                Date expirationDate = jwtTokenProvider.getExpirationDateFromToken(token);
                tokenBlacklistService.blacklistToken(token, expirationDate.getTime());
                log.info("[AUTH LOGOUT] Token blacklisted.");
            }
        }
    }

    public UUID currentUserId(String principal) {
        return UUID.fromString(principal);
    }
}
