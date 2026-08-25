package com.saanjha.modules.project.repository;

import com.saanjha.modules.project.entity.ProjectRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, UUID> {

    boolean existsByProject_IdAndRoleNameIgnoreCase(UUID projectId, String roleName);

    long countByProject_Id(UUID projectId);

    java.util.List<ProjectRequirement> findByProject_Id(UUID projectId);
}
