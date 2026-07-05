package com.saanjha.modules.project.repository;

import com.saanjha.modules.project.entity.ProjectRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, UUID> {

    boolean existsByProject_IdAndSkillNameIgnoreCase(UUID projectId, String skillName);

    long countByProject_Id(UUID projectId);
}
