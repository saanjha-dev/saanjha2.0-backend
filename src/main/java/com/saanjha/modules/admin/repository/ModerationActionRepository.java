package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.ModerationAction;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository("adminModerationActionRepository")
public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    Page<ModerationAction> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(ModerationTargetType targetType, UUID targetId, Pageable pageable);

    Page<ModerationAction> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    Page<ModerationAction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(Instant since);

    @Query("SELECT COUNT(m) FROM AdminModerationAction m WHERE m.targetType = com.saanjha.modules.admin.entity.ModerationTargetType.USER " +
           "AND m.targetId = :userId AND m.actionType IN " +
           "(com.saanjha.modules.admin.entity.ModerationActionType.USER_SUSPENDED, com.saanjha.modules.admin.entity.ModerationActionType.USER_BANNED) " +
           "AND m.reversed = false")
    long countActiveSuspensionActionsForUser(@Param("userId") UUID userId);
}
