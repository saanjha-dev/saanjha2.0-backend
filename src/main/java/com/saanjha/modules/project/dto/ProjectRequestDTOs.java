package com.saanjha.modules.project.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProjectRequestDTOs {

    private static final String CATEGORY_PATTERN =
            "^(WEB|MOBILE|AI_ML|BACKEND|DEVOPS|HACKATHON|OPEN_SOURCE|OTHER)$";
    private static final String VISIBILITY_PATTERN = "^(PUBLIC|INVITE_ONLY)$";
    private static final String SKILL_LEVEL_PATTERN = "^(BEGINNER|INTERMEDIATE|ADVANCED)$";
    private static final String TARGET_STATUS_PATTERN = "^(RECRUITING|IN_PROGRESS|COMPLETED|ARCHIVED)$";

    public record CreateProjectRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 150, message = "Title cannot exceed 150 characters")
            String title,

            @NotBlank(message = "Description is required")
            @Size(max = 5000, message = "Description cannot exceed 5000 characters")
            String description,

            @NotBlank(message = "Category is required")
            @Pattern(regexp = CATEGORY_PATTERN, message = "Invalid category")
            String category,

            @Min(value = 1, message = "A project needs room for at least 1 member")
            @Max(value = 50, message = "Team size cannot exceed 50")
            int maxTeamSize
    ) {}

    public record UpdateProjectRequest(
            @Size(max = 150, message = "Title cannot exceed 150 characters")
            String title,

            @Size(max = 5000, message = "Description cannot exceed 5000 characters")
            String description,

            @Pattern(regexp = CATEGORY_PATTERN, message = "Invalid category")
            String category,

            @Pattern(regexp = VISIBILITY_PATTERN, message = "Invalid visibility setting")
            String visibility,

            @Min(value = 1, message = "A project needs room for at least 1 member")
            @Max(value = 50, message = "Team size cannot exceed 50")
            Integer maxTeamSize
    ) {}

    public record UpdateProjectStatusRequest(
            @NotBlank(message = "Target status is required")
            @Pattern(regexp = TARGET_STATUS_PATTERN, message = "Invalid target status")
            String targetStatus,

            @Size(max = 255, message = "Reason cannot exceed 255 characters")
            String reason
    ) {}

    public record AddRequirementRequest(
            @NotBlank(message = "Role name is required")
            @Size(max = 100)
            String roleName,

            @Size(max = 20, message = "Cannot exceed 20 skills per role")
            List<@NotBlank(message = "Skill name cannot be blank") @Size(max = 100) String> skills,

            @NotBlank(message = "Skill level is required")
            @Pattern(regexp = SKILL_LEVEL_PATTERN, message = "Level must be BEGINNER, INTERMEDIATE, or ADVANCED")
            String skillLevel,

            @Min(value = 1, message = "At least 1 slot is required")
            @Max(value = 20, message = "Cannot exceed 20 slots for a single requirement")
            int slotsAvailable
    ) {}

    public record AddTagRequest(
            @NotBlank(message = "Tag name is required")
            @Size(max = 50, message = "Tag cannot exceed 50 characters")
            @Pattern(regexp = "^[a-zA-Z0-9\\-]+$", message = "Tags may only contain letters, numbers, and dashes")
            String tagName
    ) {}
}
