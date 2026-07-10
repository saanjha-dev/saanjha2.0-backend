package com.saanjha.modules.project.repository;

import com.saanjha.modules.project.entity.ProjectTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectTagRepository extends JpaRepository<ProjectTag, UUID> {

    boolean existsByProject_IdAndTagNameIgnoreCase(UUID projectId, String tagName);

    /** Backs {@link com.saanjha.modules.project.service.ProjectSnapshotProviderImpl} — the Portfolio module's technology snapshot source. */
    List<ProjectTag> findByProject_Id(UUID projectId);
}
