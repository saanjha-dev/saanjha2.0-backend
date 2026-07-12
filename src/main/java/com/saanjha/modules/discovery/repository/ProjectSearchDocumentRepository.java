package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectSearchDocumentRepository
        extends JpaRepository<ProjectSearchDocument, UUID>, ProjectSearchRepositoryCustom {
}
