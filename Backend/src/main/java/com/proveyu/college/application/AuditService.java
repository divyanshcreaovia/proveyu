package com.proveyu.college.application;

import com.proveyu.college.domain.AuditLog;
import com.proveyu.college.infrastructure.AuditLogRepository;
import com.proveyu.college.security.CollegeContext;
import com.proveyu.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    public record AuditEntry(UUID id, UUID actorUserId, String actorName, String action, String entityType, UUID entityId,
                             String summary, String details, Instant createdAt) {}

    /** Joins the caller's transaction so the audit row commits/rolls back with the change it describes. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(CollegeContext ctx, String action, String entityType, UUID entityId, String summary, String details) {
        log(ctx.collegeId(), ctx.userId(), ctx.actorName(), action, entityType, entityId, summary, details);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(UUID collegeId, UUID actorId, String actorName, String action, String entityType, UUID entityId, String summary, String details) {
        repository.save(AuditLog.builder()
                .collegeId(collegeId).actorUserId(actorId).actorName(actorName)
                .action(action).entityType(entityType).entityId(entityId)
                .summary(summary.length() > 500 ? summary.substring(0, 497) + "..." : summary)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> search(CollegeContext ctx, String entityType, String action, String q, Instant from, Instant to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("collegeId"), ctx.collegeId()));
            if (entityType != null && !entityType.isBlank()) p.add(cb.equal(root.get("entityType"), entityType));
            if (action != null && !action.isBlank()) p.add(cb.equal(root.get("action"), action));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("summary")), like), cb.like(cb.lower(root.get("actorName")), like)));
            }
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };
        var result = repository.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, a -> new AuditEntry(a.getId(), a.getActorUserId(), a.getActorName(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getSummary(), a.getDetails(), a.getCreatedAt()));
    }
}
