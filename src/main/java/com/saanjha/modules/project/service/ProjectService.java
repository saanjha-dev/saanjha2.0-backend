package com.saanjha.modules.project.service;

import com.saanjha.modules.project.dto.ProjectRequestDTOs.*;
import com.saanjha.modules.project.dto.ProjectResponseDTOs.*;
import com.saanjha.modules.project.entity.Project;
import com.saanjha.modules.project.entity.ProjectRequirement;
import com.saanjha.modules.project.entity.ProjectStatus;
import com.saanjha.modules.project.entity.ProjectStatusLog;
import com.saanjha.modules.project.entity.ProjectTag;
import com.saanjha.modules.project.event.ProjectEvents.*;
import com.saanjha.modules.project.repository.ProjectRepository;
import com.saanjha.modules.project.repository.ProjectRequirementRepository;
import com.saanjha.modules.project.repository.ProjectStatusLogRepository;
import com.saanjha.modules.project.repository.ProjectTagRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.security.HtmlSanitizer;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final int MIN_DESCRIPTION_LENGTH_TO_PUBLISH = 50;
    private static final int MIN_REQUIREMENTS_TO_PUBLISH = 1;
    private static final int MIN_TEAM_SIZE_TO_LOCK = 2;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SLUG_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final ProjectTagRepository tagRepository;
    private final ProjectStatusLogRepository statusLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ========================================================================
    // CREATION
    // ========================================================================

    @Transactional
    public ProjectResponse createProject(UUID leadUserId, CreateProjectRequest request) {
        Project project = new Project();
        project.setLeadUserId(leadUserId);
        project.setTitle(request.title().trim());
        project.setDescription(HtmlSanitizer.sanitize(request.description()));
        project.setCategory(request.category());
        project.setMaxTeamSize(request.maxTeamSize());
        project.setSlug(generateUniqueSlug(request.title()));
        project.setStatus(ProjectStatus.DRAFT);

        project = projectRepository.save(project);
        // Intentionally no event here: creating a DRAFT is CRUD, not a business event.
        return mapToResponse(project);
    }

    // ========================================================================
    // READS
    // ========================================================================

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, UUID requestingUserId) {
        Project project = getProjectOrThrow(projectId);
        assertVisible(project, requestingUserId);
        return mapToResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectBySlug(String slug) {
        Project project = projectRepository.findBySlugIgnoreCase(slug)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Project not found."));
        assertVisible(project, null);
        return mapToResponse(project);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> listMyProjects(UUID leadUserId, Pageable pageable) {
        return projectRepository.findByLeadUserId(leadUserId, pageable).map(this::mapToSummary);
    }

    /**
     * Public feed of projects actively recruiting. A deliberately minimal
     * stand-in for the future Discovery module (full-text search, skill
     * matching, fuzzy ranking) — kept here only so the platform is usable
     * end-to-end before Discovery exists.
     */
    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> listPublicRecruitingProjects(Pageable pageable) {
        return projectRepository.findByStatus(ProjectStatus.RECRUITING, pageable).map(this::mapToSummary);
    }

    @Transactional(readOnly = true)
    public List<ProjectStatusLogResponse> getStatusHistory(UUID projectId, UUID requestingUserId) {
        Project project = getProjectOrThrow(projectId);
        assertVisible(project, requestingUserId);
        return statusLogRepository.findByProjectIdOrderByChangedAtAsc(projectId).stream()
                .map(log -> new ProjectStatusLogResponse(
                        log.getFromStatus(), log.getToStatus(), log.getChangedBy(), log.getReason(), log.getChangedAt()))
                .toList();
    }

    /**
     * Internal cross-module read contract (see {@link ProjectSnapshot}'s Javadoc).
     * Unlike {@link #getProject}, this does NOT apply visibility filtering —
     * a calling module validating "can user X apply to project Y" needs the
     * real current state regardless of whether X could see it via the public
     * read path. Callers are responsible for their own authorization logic;
     * this method only answers "what does Project know to be true right now".
     */
    @Transactional(readOnly = true)
    public ProjectSnapshot getSnapshot(UUID projectId) {
        Project project = getProjectOrThrow(projectId);
        return new ProjectSnapshot(
                project.getId(),
                project.getLeadUserId(),
                project.getStatus().name(),
                project.getVisibility(),
                project.getMaxTeamSize(),
                project.getCurrentTeamSize()
        );
    }

    // ========================================================================
    // SCOPE MUTATION
    // ========================================================================

    @Transactional
    public ProjectResponse updateScope(UUID projectId, UpdateProjectRequest request) {
        Project project = getProjectOrThrow(projectId);
        assertMutable(project);

        if (request.title() != null && !request.title().isBlank()) {
            project.setTitle(request.title().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            project.setDescription(HtmlSanitizer.sanitize(request.description()));
        }
        if (request.category() != null) {
            project.setCategory(request.category());
        }
        if (request.visibility() != null) {
            project.setVisibility(request.visibility());
        }
        if (request.maxTeamSize() != null) {
            if (request.maxTeamSize() < project.getCurrentTeamSize()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Max team size cannot be reduced below the current roster size ("
                                + project.getCurrentTeamSize() + ").");
            }
            project.setMaxTeamSize(request.maxTeamSize());
        }

        project = projectRepository.save(project);
        return mapToResponse(project);
    }

    // ========================================================================
    // REQUIREMENTS MANAGEMENT
    // ========================================================================

    @Transactional
    public ProjectResponse addRequirement(UUID projectId, AddRequirementRequest request) {
        Project project = getProjectOrThrow(projectId);
        assertMutable(project);

        String normalizedSkill = request.skillName().trim();
        if (requirementRepository.existsByProject_IdAndSkillNameIgnoreCase(projectId, normalizedSkill)) {
            throw new AppException(ErrorCode.CONFLICT, "This project already has a requirement for that skill.");
        }

        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setSkillName(normalizedSkill);
        requirement.setSkillLevel(request.skillLevel().toUpperCase(Locale.ROOT));
        requirement.setSlotsAvailable(request.slotsAvailable());
        project.addRequirement(requirement);

        project = projectRepository.save(project);
        return mapToResponse(project);
    }

    @Transactional
    public ProjectResponse removeRequirement(UUID projectId, UUID requirementId) {
        Project project = getProjectOrThrow(projectId);
        assertMutable(project);

        boolean removed = project.getRequirements().removeIf(r -> r.getId().equals(requirementId));
        if (!removed) {
            throw new AppException(ErrorCode.NOT_FOUND, "Requirement not found on this project.");
        }

        project = projectRepository.save(project);
        return mapToResponse(project);
    }

    // ========================================================================
    // TAGS MANAGEMENT
    // ========================================================================

    @Transactional
    public ProjectResponse addTag(UUID projectId, AddTagRequest request) {
        Project project = getProjectOrThrow(projectId);
        assertMutable(project);

        String normalizedTag = request.tagName().trim().toLowerCase(Locale.ROOT);
        if (tagRepository.existsByProject_IdAndTagNameIgnoreCase(projectId, normalizedTag)) {
            throw new AppException(ErrorCode.CONFLICT, "This tag already exists on the project.");
        }

        ProjectTag tag = new ProjectTag();
        tag.setTagName(normalizedTag);
        project.addTag(tag);

        project = projectRepository.save(project);
        return mapToResponse(project);
    }

    @Transactional
    public ProjectResponse removeTag(UUID projectId, UUID tagId) {
        Project project = getProjectOrThrow(projectId);
        assertMutable(project);

        boolean removed = project.getTags().removeIf(t -> t.getId().equals(tagId));
        if (!removed) {
            throw new AppException(ErrorCode.NOT_FOUND, "Tag not found on this project.");
        }

        project = projectRepository.save(project);
        return mapToResponse(project);
    }

    // ========================================================================
    // STATE MACHINE
    // ========================================================================

    @Transactional
    public ProjectResponse transitionStatus(UUID projectId, UUID actingUserId, UpdateProjectStatusRequest request) {
        ProjectStatus targetStatus = ProjectStatus.valueOf(request.targetStatus());
        // Pessimistic lock closes the race window for two concurrent transition
        // requests on the same project (Spec H: "Concurrent Updates").
        Project project = projectRepository.findWithLockById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Project not found."));

        ProjectStatus currentStatus = project.getStatus();
        ProjectStatusTransitionValidator.assertLegal(currentStatus, targetStatus);
        runPreTransitionGuards(project, targetStatus);

        applyTransitionSideEffects(project, targetStatus, request.reason());
        project.setStatus(targetStatus);
        project = projectRepository.save(project);

        statusLogRepository.save(new ProjectStatusLog(projectId, currentStatus, targetStatus, actingUserId, request.reason()));
        publishTransitionEvents(project, currentStatus, targetStatus, actingUserId, request.reason());

        return mapToResponse(project);
    }

    /**
     * Internal entry point for system-triggered transitions (the ghosting
     * sweep). Bypasses the @PreAuthorize-guarded controller path entirely —
     * there is no HTTP request here, so ownership is not applicable. Reuses
     * every other guard (state machine legality, pre-transition business
     * rules) so scheduled archival can never bypass domain invariants.
     */
    @Transactional
    public void systemArchive(UUID projectId, String reason) {
        Project project = projectRepository.findWithLockById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Project not found."));

        ProjectStatus currentStatus = project.getStatus();
        if (!ProjectStatusTransitionValidator.isLegal(currentStatus, ProjectStatus.ARCHIVED)) {
            return; // Already moved on by the time the sweep runs; nothing to do.
        }

        applyTransitionSideEffects(project, ProjectStatus.ARCHIVED, reason);
        project.setStatus(ProjectStatus.ARCHIVED);
        projectRepository.save(project);

        statusLogRepository.save(new ProjectStatusLog(
                projectId, currentStatus, ProjectStatus.ARCHIVED, ProjectStatusLog.SYSTEM_ACTOR_ID, reason));
        eventPublisher.publishEvent(new ProjectArchivedEvent(
                projectId, currentStatus.name(), ProjectStatusLog.SYSTEM_ACTOR_ID, reason, Instant.now()));
    }

    // ------------------------------------------------------------------------
    // Pre-transition business rule guards (beyond raw state-machine legality)
    // ------------------------------------------------------------------------

    private void runPreTransitionGuards(Project project, ProjectStatus targetStatus) {
        if (targetStatus == ProjectStatus.RECRUITING) {
            if (project.getDescription() == null || project.getDescription().length() < MIN_DESCRIPTION_LENGTH_TO_PUBLISH) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Project description must be at least " + MIN_DESCRIPTION_LENGTH_TO_PUBLISH
                                + " characters before publishing.");
            }
            if (project.getRequirements().size() < MIN_REQUIREMENTS_TO_PUBLISH) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Define at least one skill requirement before publishing this project.");
            }
        }

        if (targetStatus == ProjectStatus.IN_PROGRESS && project.getCurrentTeamSize() < MIN_TEAM_SIZE_TO_LOCK) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Cannot lock the team with fewer than " + MIN_TEAM_SIZE_TO_LOCK + " members.");
        }
    }

    private void applyTransitionSideEffects(Project project, ProjectStatus targetStatus, String reason) {
        switch (targetStatus) {
            case RECRUITING -> project.setRecruitingStartedAt(Instant.now());
            case IN_PROGRESS -> project.setTeamLockedAt(Instant.now());
            case COMPLETED -> project.setCompletedAt(Instant.now());
            case ARCHIVED -> {
                project.setArchivedAt(Instant.now());
                project.setArchivedReason(reason);
            }
            default -> { /* DRAFT is only ever the initial state, never a transition target */ }
        }
    }

    private void publishTransitionEvents(Project project, ProjectStatus from, ProjectStatus to, UUID actingUserId, String reason) {
        Instant now = Instant.now();
        eventPublisher.publishEvent(new ProjectStatusChangedEvent(project.getId(), from.name(), to.name(), actingUserId, now));

        switch (to) {
            case RECRUITING -> {
                List<String> requiredSkills = project.getRequirements().stream()
                        .map(ProjectRequirement::getSkillName)
                        .sorted()
                        .toList();
                eventPublisher.publishEvent(new ProjectPublishedEvent(
                        project.getId(), project.getLeadUserId(), project.getTitle(), requiredSkills, now));
            }
            case COMPLETED -> eventPublisher.publishEvent(
                    new ProjectCompletedEvent(project.getId(), project.getLeadUserId(), now));
            case ARCHIVED -> eventPublisher.publishEvent(
                    new ProjectArchivedEvent(project.getId(), from.name(), actingUserId, reason, now));
            default -> { /* IN_PROGRESS currently has no dedicated event beyond the generic status-changed signal */ }
        }
    }

    // ========================================================================
    // GUARDS & HELPERS
    // ========================================================================

    private Project getProjectOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Project not found."));
    }

    private void assertMutable(Project project) {
        if (!project.isMutable()) {
            throw new AppException(ErrorCode.PROJECT_READ_ONLY,
                    "This project is " + project.getStatus() + " and can no longer be modified.");
        }
    }

    /**
     * DRAFT and ARCHIVED projects are only visible to their Lead (Admin access
     * is granted separately at the controller/@PreAuthorize layer for
     * moderation endpoints, not through this read path). Everything else
     * (RECRUITING, IN_PROGRESS, COMPLETED) is public, matching the security
     * matrix's "View public projects" row for GUEST.
     */
    private void assertVisible(Project project, UUID requestingUserId) {
        boolean restricted = project.getStatus() == ProjectStatus.DRAFT || project.getStatus() == ProjectStatus.ARCHIVED;
        if (restricted && (requestingUserId == null || !requestingUserId.equals(project.getLeadUserId()))) {
            // 404, not 403: don't leak the existence of a private/archived resource.
            throw new AppException(ErrorCode.NOT_FOUND, "Project not found.");
        }
    }

    private String generateUniqueSlug(String title) {
        String base = title.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");
        if (base.length() > 140) {
            base = base.substring(0, 140);
        }
        if (base.isBlank()) {
            base = "project";
        }

        String candidate;
        do {
            candidate = base + "-" + randomSuffix();
        } while (projectRepository.existsBySlugIgnoreCase(candidate));

        return candidate;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(SLUG_ALPHABET.charAt(RANDOM.nextInt(SLUG_ALPHABET.length())));
        }
        return sb.toString();
    }

    private ProjectResponse mapToResponse(Project project) {
        List<ProjectRequirementResponse> requirements = project.getRequirements().stream()
                .sorted(Comparator.comparing(ProjectRequirement::getSkillName))
                .map(r -> new ProjectRequirementResponse(r.getId(), r.getSkillName(), r.getSkillLevel(), r.getSlotsAvailable()))
                .collect(Collectors.toList());

        List<String> tagNames = project.getTags().stream()
                .map(ProjectTag::getTagName)
                .sorted()
                .toList();

        return new ProjectResponse(
                project.getId(),
                project.getSlug(),
                project.getLeadUserId(),
                project.getTitle(),
                project.getDescription(),
                project.getStatus().name(),
                project.getCategory(),
                project.getVisibility(),
                project.getMaxTeamSize(),
                project.getCurrentTeamSize(),
                project.getRecruitingStartedAt(),
                project.getTeamLockedAt(),
                project.getCompletedAt(),
                project.getArchivedAt(),
                requirements,
                tagNames,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private ProjectSummaryResponse mapToSummary(Project project) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getSlug(),
                project.getTitle(),
                project.getStatus().name(),
                project.getCategory(),
                project.getMaxTeamSize(),
                project.getCurrentTeamSize(),
                project.getCreatedAt()
        );
    }
}
