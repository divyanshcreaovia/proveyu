package com.proveyu.admin.application;

import com.proveyu.admin.domain.AdminAuditLog;
import com.proveyu.admin.domain.AdminModel.AuditPage;
import com.proveyu.admin.domain.AdminModel.AuditRow;
import com.proveyu.admin.infrastructure.AdminAuditLogRepository;
import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.shared.audit.AdminActionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Admin audit log: what each platform admin did and when. Other modules report through {@link AdminActionEvent}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository repository;
    private final UserRepository users;

    @EventListener
    public void on(AdminActionEvent e) {
        log(e.adminId(), e.action(), e.targetType(), e.targetId(), e.summary());
    }

    /** Never fails the admin's action: a log write problem is only logged. */
    @Transactional
    public void log(UUID adminId, String action, String targetType, String targetId, String summary) {
        try {
            String name = adminId == null ? "System" : users.findById(adminId).map(User::getFullName).orElse("Admin");
            repository.save(AdminAuditLog.builder().adminId(adminId).adminName(name).action(cut(action, 60)).targetType(cut(targetType, 40))
                    .targetId(cut(targetId, 64)).summary(cut(summary, 500)).build());
        } catch (RuntimeException ex) {
            log.warn("Admin audit entry not saved ({} {}): {}", action, targetId, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AuditPage list(String actionPrefix, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<AdminAuditLog> r = actionPrefix == null || actionPrefix.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc(p) : repository.findAllByActionStartingWithOrderByCreatedAtDesc(actionPrefix.trim(), p);
        return new AuditPage(r.getContent().stream().map(AdminAuditService::row).toList(), r.getTotalElements(), p.getPageNumber(), p.getPageSize());
    }

    @Transactional(readOnly = true)
    public List<AuditRow> recent(int n) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, n)).getContent().stream().map(AdminAuditService::row).toList();
    }

    private static AuditRow row(AdminAuditLog a) {
        return new AuditRow(a.getId(), a.getAdminName(), a.getAction(), a.getTargetType(), a.getTargetId(), a.getSummary(), a.getCreatedAt());
    }

    private static String cut(String s, int n) {
        return s == null ? null : s.length() > n ? s.substring(0, n) : s;
    }
}
