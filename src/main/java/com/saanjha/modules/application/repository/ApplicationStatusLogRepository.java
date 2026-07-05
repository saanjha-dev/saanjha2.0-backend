package com.saanjha.modules.application.repository;

import com.saanjha.modules.application.entity.ApplicationStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationStatusLogRepository extends JpaRepository<ApplicationStatusLog, UUID> {

    List<ApplicationStatusLog> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}
