package com.proveyu.shared.security;

import com.proveyu.shared.error.DomainException;
import org.springframework.http.HttpStatus;

/** One password rule everywhere: 8 to 100 characters with at least one letter and one number. */
public final class PasswordRules {
    private PasswordRules() {}

    public static void require(String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 100 || !raw.matches("(?s)^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            throw new DomainException("Password must be at least 8 characters and contain letters and numbers", HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
        }
    }
}
