package com.saanjha.modules.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserResponseDTOs {

    public record UserProfileResponse(
            UUID id,
            String displayName,
            String headline,
            String bio,
            String location,
            String college,
            String experienceLevel,
            String profileImageUrl,
            int profileScore,
            int projectsCompleted,
            UserPreferencesResponse preferences,
            List<UserSkillResponse> skills,
            List<UserInterestResponse> interests,
            List<UserSocialLinkResponse> socialLinks
    ) {}

    public record UserSkillResponse(
            UUID id,
            String skillName,
            String skillLevel,
            boolean isVerified,
            Instant verifiedAt
    ) {}

    public record UserInterestResponse(
            UUID id,
            String interestName
    ) {}

    public record UserSocialLinkResponse(
            UUID id,
            String platformName,
            String url
    ) {}

    public record UserPreferencesResponse(
            String theme,
            boolean emailNotifications,
            String profileVisibility
    ) {}

    public record ProfilePhotoResponse(
            String profileImageUrl
    ) {}
}