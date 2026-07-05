package com.saanjha.modules.team.repository;

import com.saanjha.modules.team.entity.MembershipHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MembershipHistoryRepository extends JpaRepository<MembershipHistory, UUID> {

    Page<MembershipHistory> findByTeamIdOrderByOccurredAtDesc(UUID teamId, Pageable pageable);

    List<MembershipHistory> findByMembershipIdOrderByOccurredAtAsc(UUID membershipId);
}
