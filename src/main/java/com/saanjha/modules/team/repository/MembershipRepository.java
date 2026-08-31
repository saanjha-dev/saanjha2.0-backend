package com.saanjha.modules.team.repository;

import com.saanjha.modules.team.entity.Membership;
import com.saanjha.modules.team.entity.MembershipRole;
import com.saanjha.modules.team.entity.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Page<Membership> findByTeam_IdAndStatusIn(UUID teamId, List<MembershipStatus> statuses, Pageable pageable);

    /**
     * P0-1 (Workspace/Team Discovery): every active/suspended membership a
     * user holds, across every team, for the "my workspaces" read model.
     * {@code @EntityGraph} eager-fetches the owning Team in the same query
     * (a JOIN, not a second round trip) so mapping each row to a
     * {@code TeamResponse} never triggers the lazy {@code Membership.team}
     * association per-row — the exact N+1 shape called out in the brief.
     */
    @EntityGraph(attributePaths = "team")
    Page<Membership> findByUserIdAndStatusIn(UUID userId, List<MembershipStatus> statuses, Pageable pageable);

    Page<Membership> findByTeam_Id(UUID teamId, Pageable pageable);

    Optional<Membership> findByTeam_IdAndUserIdAndStatusIn(UUID teamId, UUID userId, List<MembershipStatus> liveStatuses);

    Optional<Membership> findByTeam_IdAndRoleAndStatus(UUID teamId, MembershipRole role, MembershipStatus status);

    boolean existsBySourceReferenceId(UUID sourceReferenceId);

    Optional<Membership> findBySourceReferenceId(UUID sourceReferenceId);

    List<Membership> findByTeam_IdAndStatusIn(UUID teamId, List<MembershipStatus> statuses);

    long countByUserIdAndStatus(UUID userId, MembershipStatus status);
}
