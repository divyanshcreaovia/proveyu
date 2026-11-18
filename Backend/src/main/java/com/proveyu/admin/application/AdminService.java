package com.proveyu.admin.application;

import com.proveyu.auth.domain.Role;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.shared.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform admin accounts (the admin pages themselves live in the college, prep and interview modules). */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createAdminUser(String email, String fullName, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email is already registered", HttpStatus.BAD_REQUEST, "EMAIL_EXISTS");
        }

        User adminUser = User.builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .active(true)
                .build();

        return userRepository.save(adminUser);
    }
}
