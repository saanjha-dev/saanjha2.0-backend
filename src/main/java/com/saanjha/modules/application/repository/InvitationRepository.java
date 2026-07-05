package com.saanjha.modules.application.repository;

import com.saanjha.modules.application.entity.Invitation;
import com.saanjha.modules.application.entity.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Page<Invitation> findByInvitedUserId(UUID invitedUserId, Pageable pageable);

    Page<Invitation> findByProjectId(UUID projectId, Pageable pageable);

    boolean existsByProjectIdAndInvitedUserIdAndStatus(UUID projectId, UUID invitedUserId, InvitationStatus status);

    List<Invitation> findByStatusAndExpiresAtBefore(InvitationStatus status, Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findWithLockById(UUID id);
}
