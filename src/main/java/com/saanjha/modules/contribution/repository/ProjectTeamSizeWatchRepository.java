package com.saanjha.modules.contribution.repository;

import com.saanjha.modules.contribution.entity.ProjectTeamSizeWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectTeamSizeWatchRepository extends JpaRepository<ProjectTeamSizeWatch, UUID> {
}
