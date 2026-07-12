package com.saanjha.modules.discovery.repository;

import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeveloperSearchDocumentRepository
        extends JpaRepository<DeveloperSearchDocument, UUID>, DeveloperSearchRepositoryCustom {
}
