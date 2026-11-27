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

    /** Name or part of an e-mail finds only recruiters this college already works with; anyone else only by the full e-mail address. */
    @org.springframework.data.jpa.repository.Query("select u from User u where u.role = com.proveyu.auth.domain.Role.RECRUITER and u.active = true and "
            + "(lower(u.email) = lower(:q) or ((lower(u.fullName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%'))) "
            + "and (exists (select 1 from CollegeCompany c where c.collegeId = :collegeId and c.linkedRecruiterId = u.id) "
            + "or exists (select 1 from DriveRecruiter d where d.collegeId = :collegeId and d.userId = u.id))))")
    java.util.List<User> searchRecruiters(@org.springframework.data.repository.query.Param("q") String q,
            @org.springframework.data.repository.query.Param("collegeId") java.util.UUID collegeId, org.springframework.data.domain.Pageable pageable);
    boolean existsByPhone(String phone);
}
