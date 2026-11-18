package com.proveyu.admin.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/**
 * The first platform admin of a new installation. There is no admin sign-up, so when no admin exists yet and
 * ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD are set, that account is created at start. Once any admin exists this does
 * nothing: further admins are created by an admin inside the portal. Remove the variables after the first start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.email:}")
    private String email;
    @Value("${admin.bootstrap.password:}")
    private String password;
    @Value("${admin.bootstrap.name:Platform admin}")
    private String name;

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) return;
        if (users.findAll().stream().anyMatch(u -> u.getRole() == Role.ADMIN)) return;
        if (password.length() < 8) {
            log.warn("ADMIN_BOOTSTRAP_PASSWORD is shorter than 8 characters: first admin not created");
            return;
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(e)) {
            log.warn("ADMIN_BOOTSTRAP_EMAIL {} already belongs to another account: first admin not created", e);
            return;
        }
        users.save(User.builder().email(e).fullName(name).passwordHash(passwordEncoder.encode(password)).role(Role.ADMIN).active(true)
                .emailVerifiedAt(Instant.now()).build());
        log.info("First platform admin {} created. Remove ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD now.", e);
    }
}
