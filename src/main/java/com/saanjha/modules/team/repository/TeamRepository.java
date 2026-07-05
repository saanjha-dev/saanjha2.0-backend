package com.saanjha.modules.team.repository;

import com.saanjha.modules.team.entity.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);

    /**
     * Pessimistic write lock — the serialization point for every roster
     * mutation (transfers, adds, removals) on this team. See the V10
     * migration's comment on {@code version} for why Team itself, not
     * Membership, is the lock target.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Team> findWithLockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Team> findWithLockByProjectId(UUID projectId);
}
