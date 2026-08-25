package com.saanjha.modules.project.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProjectResponseDTOs {

    public record ProjectResponse(
            UUID id,
            String slug,
            UUID leadUserId,
            String title,
            String description,
            String status,
            String category,
            String visibility,
            int maxTeamSize,
            int currentTeamSize,
            Instant recruitingStartedAt,
            Instant teamLockedAt,
            Instant completedAt,
            Instant archivedAt,
            List<ProjectRequirementResponse> requirements,
            List<String> tags,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ProjectSummaryResponse(
            UUID id,
            String slug,
            String title,
            String status,
            String category,
            int maxTeamSize,
            int currentTeamSize,
            Instant createdAt
    ) {}

    public record ProjectRequirementResponse(
            UUID id,
            String roleName,
            List<String> skills,
            String skillLevel,
            int slotsAvailable
    ) {}

    public record ProjectStatusLogResponse(
            String fromStatus,
            String toStatus,
            UUID changedBy,
            String reason,
            Instant changedAt
    ) {}

    public record ProjectMutationResponse(
            String message,
            String status
    ) {}

    /**
     * A deliberately narrow, internal-only read contract for other modules
     * (first consumer: Application) that need to validate against a project's
     * current state without depending on the full public {@link ProjectResponse}
     * shape or bypassing visibility rules meant for end-user-facing reads.
     * This IS the sanctioned "service interface" cross-module call the
     * architecture boundary rule requires — never replace this with direct
     * repository access from another module's package.
     */
    public record ProjectSnapshot(
            UUID id,
            UUID leadUserId,
            String status,
            String visibility,
            int maxTeamSize,
            int currentTeamSize
    ) {}
}
