package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.ProjectModerationOverlay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectModerationOverlayRepository extends JpaRepository<ProjectModerationOverlay, UUID> {

    Optional<ProjectModerationOverlay> findByProjectId(UUID projectId);

    long countByFeaturedTrue();

    long countByHiddenTrue();
}
