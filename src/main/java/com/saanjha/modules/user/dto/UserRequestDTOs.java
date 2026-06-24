package com.saanjha.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public class UserRequestDTOs {

    public record UpdateProfileRequest(
            @Size(max = 100, message = "Display name cannot exceed 100 characters")
            String displayName,

            @Pattern(regexp = "^[a-z0-9_\\-]+$", message = "Handle can only contain lowercase alphanumeric characters, dashes, or underscores")
            @Size(min = 3, max = 50, message = "Handle must be between 3 and 50 characters")
            String uniqueHandle,

            @Size(max = 150, message = "Headline cannot exceed 150 characters")
            String headline,

            @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
            String bio,

            @Size(max = 100, message = "Location cannot exceed 100 characters")
            String location,

            @Size(max = 200, message = "College name cannot exceed 200 characters")
            String college,

            @Pattern(regexp = "^(ENTRY_LEVEL|MID_LEVEL|SENIOR|LEAD)$", message = "Invalid experience level")
            String experienceLevel
    ) {}

    public record AddSkillRequest(
            @NotBlank(message = "Skill name is required")
            @Size(max = 100)
            String skillName,

            @NotBlank(message = "Skill level is required")
            @Pattern(regexp = "^(BEGINNER|INTERMEDIATE|ADVANCED)$", message = "Level must be BEGINNER, INTERMEDIATE, or ADVANCED")
            String skillLevel
    ) {}

    public record AddInterestRequest(
            @NotBlank(message = "Interest name is required")
            @Size(max = 100)
            String interestName
    ) {}

    public record AddSocialLinkRequest(
            @NotBlank(message = "Platform name is required")
            @Pattern(regexp = "^(GITHUB|LINKEDIN|PORTFOLIO|X)$", message = "Unsupported platform")
            String platformName,

            @NotBlank(message = "URL is required")
            @URL(message = "Must be a valid URL format")
            String url
    ) {}

    public record UpdatePreferencesRequest(
            @Pattern(regexp = "^(DARK|LIGHT|SYSTEM)$", message = "Invalid theme")
            String theme,

            boolean emailNotifications,

            @Pattern(regexp = "^(PUBLIC|PRIVATE|CONNECTIONS_ONLY)$", message = "Invalid visibility setting")
            String profileVisibility
    ) {}
}