package com.proveyu.admin.infrastructure;

import com.proveyu.admin.domain.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable page);

    Page<AdminAuditLog> findAllByActionStartingWithOrderByCreatedAtDesc(String actionPrefix, Pageable page);
}
