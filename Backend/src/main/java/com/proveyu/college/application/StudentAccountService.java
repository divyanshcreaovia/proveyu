package com.proveyu.college.application;

import com.proveyu.auth.application.AuthService;
import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Links a college student record to a CANDIDATE login (creating one with a random password if needed). */
@Service
@RequiredArgsConstructor
public class StudentAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record Account(UUID userId, boolean created) {}

    @Transactional
    public Account ensureCandidate(String rawEmail, String fullName, String phone) {
        String email = AuthService.normalizeEmail(rawEmail);
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.getRole() != Role.CANDIDATE) {
                throw new DomainException("E-mail " + email + " belongs to a non-student account", HttpStatus.CONFLICT, "EMAIL_IN_USE");
            }
            return new Account(u.getId(), false);
        }
        String safePhone = (phone != null && !phone.isBlank() && !userRepository.existsByPhone(phone) && phone.length() <= 15) ? phone : null;
        User u = userRepository.save(User.builder()
                .email(email).fullName(fullName).phone(safePhone).role(Role.CANDIDATE).active(true)
                // random unusable password: student sets their own via "Forgot password" (OTP)
                .passwordHash(passwordEncoder.encode(TokenUtils.randomUrlToken()))
                .emailVerifiedAt(Instant.now())
                .build());
        return new Account(u.getId(), true);
    }
}
