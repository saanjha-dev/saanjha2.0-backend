package com.saanjha.modules.contribution.repository;

import com.saanjha.modules.contribution.entity.ContributionSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContributionSnapshotRepository extends JpaRepository<ContributionSnapshot, UUID> {

    Page<ContributionSnapshot> findByUserIdOrderByCapturedAtDesc(UUID userId, Pageable pageable);
}
