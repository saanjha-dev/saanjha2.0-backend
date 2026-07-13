package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.DashboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, UUID> {

    List<DashboardSnapshot> findTop30ByOrderByCapturedAtDesc();
}
