package com.proveyu.auth.infrastructure;

import com.proveyu.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetToken(String resetToken);
    boolean existsByEmail(String email);
    java.util.List<User> findAllByIdIn(java.util.Collection<java.util.UUID> ids);

    @org.springframework.data.jpa.repository.Query("select u from User u where u.role = com.proveyu.auth.domain.Role.RECRUITER and u.active = true and "
            + "(lower(u.fullName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%')))")
    java.util.List<User> searchRecruiters(@org.springframework.data.repository.query.Param("q") String q, org.springframework.data.domain.Pageable pageable);
    boolean existsByPhone(String phone);
}
