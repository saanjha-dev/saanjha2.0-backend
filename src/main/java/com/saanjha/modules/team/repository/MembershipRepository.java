package com.saanjha.modules.team.repository;

import com.saanjha.modules.team.entity.Membership;
import com.saanjha.modules.team.entity.MembershipRole;
import com.saanjha.modules.team.entity.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Page<Membership> findByTeam_IdAndStatusIn(UUID teamId, List<MembershipStatus> statuses, Pageable pageable);

    Page<Membership> findByTeam_Id(UUID teamId, Pageable pageable);

    Optional<Membership> findByTeam_IdAndUserIdAndStatusIn(UUID teamId, UUID userId, List<MembershipStatus> liveStatuses);

    Optional<Membership> findByTeam_IdAndRoleAndStatus(UUID teamId, MembershipRole role, MembershipStatus status);

    boolean existsBySourceReferenceId(UUID sourceReferenceId);

    Optional<Membership> findBySourceReferenceId(UUID sourceReferenceId);

    List<Membership> findByTeam_IdAndStatusIn(UUID teamId, List<MembershipStatus> statuses);

    long countByUserIdAndStatus(UUID userId, MembershipStatus status);
}
