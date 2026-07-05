package com.saanjha.modules.project.repository;

import com.saanjha.modules.project.entity.ProjectTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectTagRepository extends JpaRepository<ProjectTag, UUID> {

    boolean existsByProject_IdAndTagNameIgnoreCase(UUID projectId, String tagName);
}
