package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.AdminAuditLog;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Deliberately exposes no update/delete methods anywhere in this interface —
 * {@link AdminAuditLog} is append-only by contract. See the entity's javadoc.
 */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findByActorIdOrderByOccurredAtDesc(UUID actorId, Pageable pageable);

    Page<AdminAuditLog> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(ModerationTargetType targetType, UUID targetId, Pageable pageable);

    Page<AdminAuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Page<AdminAuditLog> findByRequestIdOrderByOccurredAtDesc(String requestId, Pageable pageable);
}
