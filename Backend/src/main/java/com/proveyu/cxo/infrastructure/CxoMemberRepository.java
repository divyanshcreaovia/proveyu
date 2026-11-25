package com.proveyu.cxo.infrastructure;

import com.proveyu.cxo.domain.CxoMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CxoMemberRepository extends JpaRepository<CxoMember, UUID> {
    Optional<CxoMember> findByUserId(UUID userId);
}
