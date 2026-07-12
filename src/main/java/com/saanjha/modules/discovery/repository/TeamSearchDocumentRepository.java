package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.TeamSearchDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamSearchDocumentRepository extends JpaRepository<TeamSearchDocument, UUID> {
    List<TeamSearchDocument> findByStatusAndCurrentSizeLessThan(String status, int maxSize);
    TeamSearchDocument findByProjectId(UUID projectId);
}
