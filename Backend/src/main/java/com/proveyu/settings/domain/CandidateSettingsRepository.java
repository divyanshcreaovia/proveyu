package com.proveyu.settings.domain;

import com.proveyu.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateSettingsRepository extends JpaRepository<CandidateSettings, UUID> {
    Optional<CandidateSettings> findByUser(User user);
}
