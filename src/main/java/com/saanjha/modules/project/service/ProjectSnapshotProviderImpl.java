package com.saanjha.modules.project.service;

import com.saanjha.modules.project.entity.Project;
import com.saanjha.modules.project.entity.ProjectTag;
import com.saanjha.modules.project.repository.ProjectRepository;
import com.saanjha.modules.project.repository.ProjectTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectSnapshotProviderImpl implements ProjectSnapshotProvider {

    private static final int DESCRIPTION_EXCERPT_LENGTH = 280;

    private final ProjectRepository projectRepository;
    private final ProjectTagRepository projectTagRepository;
    private final com.saanjha.modules.project.repository.ProjectRequirementRepository projectRequirementRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSnapshot> getSnapshot(UUID projectId) {
        return projectRepository.findById(projectId).map(project -> {
            List<String> tags = projectTagRepository.findByProject_Id(projectId).stream()
                    .map(ProjectTag::getTagName)
                    .toList();
            List<ProjectSnapshotProvider.RequirementSnapshot> requirements = projectRequirementRepository.findByProject_Id(projectId).stream()
                    .map(req -> new ProjectSnapshotProvider.RequirementSnapshot(req.getRoleName(), req.getSkills(), req.getSkillLevel()))
                    .toList();
            return new ProjectSnapshot(
                    project.getId(),
                    project.getTitle(),
                    project.getSlug(),
                    project.getCategory(),
                    excerpt(project.getDescription()),
                    tags,
                    requirements
            );
        });
    }

    private String excerpt(String description) {
        if (description == null || description.length() <= DESCRIPTION_EXCERPT_LENGTH) {
            return description;
        }
        return description.substring(0, DESCRIPTION_EXCERPT_LENGTH).stripTrailing() + "\u2026";
    }
}
