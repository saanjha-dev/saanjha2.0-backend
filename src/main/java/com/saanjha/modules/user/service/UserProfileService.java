package com.saanjha.modules.user.service;

import com.saanjha.modules.user.entity.*;
import com.saanjha.modules.user.dto.UserRequestDTOs.*;
import com.saanjha.modules.user.dto.UserResponseDTOs.*;
import com.saanjha.modules.user.event.UserEvents.ProfileCompletedEvent;
import com.saanjha.modules.user.event.UserEvents.ProfileUpdatedEvent;
import com.saanjha.modules.user.repository.*;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.storage.CloudinaryService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final UserSkillRepository skillRepository;
    private final UserInterestRepository interestRepository;
    private final UserSocialLinkRepository socialLinkRepository;
    private final UserPreferencesRepository preferencesRepository;

    private final CloudinaryService cloudinaryService;

    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final int COMPLETION_THRESHOLD = 80;

    // ========================================================================
    // PROFILE MANAGEMENT
    // ========================================================================

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserProfile profile = getProfileEntity(userId);

        int score = getOrCalculateProfileScore(profile);

        return mapToProfileResponse(profile, score);
    }

    @Transactional
    public void updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = getProfileEntity(userId);

        profile.setDisplayName(request.displayName());
        profile.setHeadline(request.headline());
        profile.setBio(request.bio());
        profile.setLocation(request.location());
        profile.setCollege(request.college());
        profile.setExperienceLevel(request.experienceLevel());

        // ADDED: Unique Handle Duplicate Guard Check
        if (request.uniqueHandle() != null && !request.uniqueHandle().trim().isEmpty()) {
            String normalizedHandle = request.uniqueHandle().trim().toLowerCase();

            // Check if another user has already claimed this handle
            if (profileRepository.existsByUniqueHandleIgnoreCaseAndUserIdNot(normalizedHandle, userId)) {
                throw new AppException(ErrorCode.CONFLICT, "This unique handle is already claimed.");
            }
            profile.setUniqueHandle(normalizedHandle);
        }

        profileRepository.save(profile);
        triggerProfileUpdatePipeline(profile);
    }

    // ========================================================================
    // SKILLS MANAGEMENT
    // ========================================================================

    @Transactional
    public void addSkill(UUID userId, AddSkillRequest request) {
        UserProfile profile = getProfileEntity(userId);
        String normalizedSkill = normalizeString(request.skillName());

        if (skillRepository.existsByProfile_UserIdAndSkillNameIgnoreCase(userId, normalizedSkill)) {
            throw new AppException(ErrorCode.CONFLICT, "Skill already exists on this profile.");
        }

        UserSkill skill = new UserSkill();
        skill.setProfile(profile);
        skill.setSkillName(normalizedSkill);
        skill.setSkillLevel(request.skillLevel().toUpperCase());

        skillRepository.save(skill);
        triggerProfileUpdatePipeline(profile);
    }

    @Transactional
    public void removeSkill(UUID userId, UUID skillId) {
        skillRepository.deleteByIdAndProfile_UserId(skillId, userId);
        triggerProfileUpdatePipeline(getProfileEntity(userId));
    }

    // ========================================================================
    // INTERESTS MANAGEMENT
    // ========================================================================

    @Transactional
    public void addInterest(UUID userId, AddInterestRequest request) {
        UserProfile profile = getProfileEntity(userId);
        String normalizedInterest = normalizeString(request.interestName());

        if (interestRepository.existsByProfile_UserIdAndInterestNameIgnoreCase(userId, normalizedInterest)) {
            throw new AppException(ErrorCode.CONFLICT, "Interest already exists on this profile.");
        }

        UserInterest interest = new UserInterest();
        interest.setProfile(profile);
        interest.setInterestName(normalizedInterest);

        interestRepository.save(interest);
        triggerProfileUpdatePipeline(profile);
    }

    @Transactional
    public void removeInterest(UUID userId, UUID interestId) {
        interestRepository.deleteByIdAndProfile_UserId(interestId, userId);
        triggerProfileUpdatePipeline(getProfileEntity(userId));
    }

    // ========================================================================
    // SOCIAL LINKS MANAGEMENT
    // ========================================================================

    @Transactional
    public void addSocialLink(UUID userId, AddSocialLinkRequest request) {
        UserProfile profile = getProfileEntity(userId);
        String platform = request.platformName().toUpperCase();

        if (socialLinkRepository.existsByProfile_UserIdAndPlatformNameIgnoreCase(userId, platform)) {
            throw new AppException(ErrorCode.CONFLICT, "A link for this platform already exists. Please update or delete it first.");
        }

        UserSocialLink link = new UserSocialLink();
        link.setProfile(profile);
        link.setPlatformName(platform);
        link.setUrl(request.url());

        socialLinkRepository.save(link);
        triggerProfileUpdatePipeline(profile);
    }

    @Transactional
    public void removeSocialLink(UUID userId, UUID linkId) {
        socialLinkRepository.deleteByIdAndProfile_UserId(linkId, userId);
        triggerProfileUpdatePipeline(getProfileEntity(userId));
    }

    // ========================================================================
    // PREFERENCES MANAGEMENT
    // ========================================================================

    @Transactional
    public void updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        UserPreferences prefs = preferencesRepository.findByProfile_UserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Preferences not found."));

        prefs.setTheme(request.theme());
        prefs.setEmailNotifications(request.emailNotifications());
        prefs.setProfileVisibility(request.profileVisibility());

        preferencesRepository.save(prefs);
    }

    @Transactional
    public String uploadProfilePhoto(UUID userId, org.springframework.web.multipart.MultipartFile file) {
        UserProfile profile = getProfileEntity(userId);

        // 1. Upload to Cloudinary (Stores in folder 'users' with their UUID as the filename)
        String secureUrl = cloudinaryService.uploadImage(file, "users", userId);

        // 2. Save the URL to the database
        profile.setProfileImageUrl(secureUrl);
        profileRepository.save(profile);

        // 3. Trigger the update pipeline (This recalculates their profile score!)
        triggerProfileUpdatePipeline(profile);

        return secureUrl;
    }

    // ========================================================================
    // MASTER ENGINEERED UTILITIES & CACHING
    // ========================================================================

    private UserProfile getProfileEntity(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User profile not found."));
    }

    private String normalizeString(String input) {
        if (input == null) return null;
        // Trims, lowercases, and removes dangerous special characters while keeping C++, C#, etc.
        return input.trim().toLowerCase().replaceAll("[^a-z0-9+#.\\- ]", "");
    }

    /**
     * Executes the post-update pipeline: Recalculates score, caches it, and publishes events.
     */
    private void triggerProfileUpdatePipeline(UserProfile profile) {
        int oldScore = profile.getProfileScore();
        int newScore = calculateAndCacheProfileScore(profile);

        // Update the materialized score in the DB
        profile.setProfileScore(newScore);
        profileRepository.save(profile);

        // Announce to the system that the profile changed
        eventPublisher.publishEvent(new ProfileUpdatedEvent(profile.getUserId()));

        // If they crossed the threshold for the first time, fire the achievement event
        if (oldScore < COMPLETION_THRESHOLD && newScore >= COMPLETION_THRESHOLD) {
            eventPublisher.publishEvent(new ProfileCompletedEvent(profile.getUserId()));
        }
    }

    private int getOrCalculateProfileScore(UserProfile profile) {
        String redisKey = "user:profile_score:" + profile.getUserId();
        String cachedScore = redisTemplate.opsForValue().get(redisKey);

        if (cachedScore != null) {
            return Integer.parseInt(cachedScore);
        }
        return calculateAndCacheProfileScore(profile);
    }

    private int calculateAndCacheProfileScore(UserProfile profile) {
        int score = 0;

        // 1. Basic Info (Max 25%)
        if (profile.getProfileImageUrl() != null) score += 10;
        if (profile.getHeadline() != null && !profile.getHeadline().isEmpty()) score += 5;
        if (profile.getBio() != null && !profile.getBio().isEmpty()) score += 10;

        // 2. Experience & Location (Max 15%)
        if (profile.getLocation() != null) score += 5;
        if (profile.getExperienceLevel() != null) score += 10;

        // 3. Relational Data (Max 40%)
        if (!profile.getSkills().isEmpty()) score += 20; // Has at least 1 skill
        if (!profile.getInterests().isEmpty()) score += 10;
        if (!profile.getSocialLinks().isEmpty()) score += 10;

        // 4. Platform Activity (Max 20%)
        if (profile.getProjectsCompleted() > 0) score += 20;

        // Cap at 100
        score = Math.min(score, 100);

        // Save to Redis with a 24-hour TTL (recalculated automatically on updates anyway)
        String redisKey = "user:profile_score:" + profile.getUserId();
        redisTemplate.opsForValue().set(redisKey, String.valueOf(score), java.time.Duration.ofHours(24));

        return score;
    }

    // ========================================================================
    // ACCOUNT DELETION (SOFT DELETE)
    // ========================================================================

    @Transactional
    public void softDeleteProfile(UUID userId) {
        UserProfile profile = getProfileEntity(userId);

        // 1. Mark the profile as deleted
        profile.setDeleted(true);
        profileRepository.save(profile);

        // 2. Clear their Redis Score Cache
        String redisKey = "user:profile_score:" + userId;
        redisTemplate.delete(redisKey);

        // 3. Publish an event so the Auth module knows to revoke their active JWT sessions
        // eventPublisher.publishEvent(new ProfileDeletedEvent(userId));
    }

    // ========================================================================
    // PUBLIC PROFILE VIEW (PRIVACY-MASKED)
    // ========================================================================

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(UUID targetUserId, UUID requesterId) {
        UserProfile profile = getProfileEntity(targetUserId);

        // 1. Evaluate Privacy Guardrails
        String visibility = profile.getPreferences() != null
                ? profile.getPreferences().getProfileVisibility()
                : "PUBLIC";

        // If the requester is looking at their own public profile link, bypass guards
        if (!targetUserId.equals(requesterId)) {
            if ("PRIVATE".equalsIgnoreCase(visibility)) {
                throw new AppException(ErrorCode.FORBIDDEN, "This profile is private.");
            } else if ("CONNECTIONS_ONLY".equalsIgnoreCase(visibility)) {
                // TODO: When the Connections Module is built, inject ConnectionService here to verify relationship
                throw new AppException(ErrorCode.FORBIDDEN, "You must be connected with this user to view their profile.");
            }
        }

        // 2. Fetch Score and Map to Safe DTO
        int score = getOrCalculateProfileScore(profile);
        return mapToPublicProfileResponse(profile, score);
    }

    // ========================================================================
    // DEDICATED SLUG LOOKUP
    // ========================================================================

    @Transactional(readOnly = true)
    public PublicProfileResponse getProfileByHandle(String handle) {
        // Strip out '@' symbol if passed by the frontend routing engine
        String sanitizedHandle = handle.startsWith("@") ? handle.substring(1) : handle;

        UserProfile profile = profileRepository.findByUniqueHandleIgnoreCase(sanitizedHandle.trim())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Profile with handle @" + sanitizedHandle + " not found."));

        // Enforce privacy guardrails identically to direct UUID queries
        String visibility = profile.getPreferences() != null ? profile.getPreferences().getProfileVisibility() : "PUBLIC";
        if ("PRIVATE".equalsIgnoreCase(visibility)) {
            throw new AppException(ErrorCode.FORBIDDEN, "This profile is private.");
        }

        return mapToPublicProfileResponse(profile, getOrCalculateProfileScore(profile));
    }

    // ========================================================================
    // GDPR COMPLIANCE DATA EXPORT
    // ========================================================================

    @Transactional(readOnly = true)
    public UserProfileResponse exportUserData(UUID userId) {
        // Fetch the full database snapshot belonging to the user context
        UserProfile profile = getProfileEntity(userId);
        return mapToProfileResponse(profile, getOrCalculateProfileScore(profile));
    }

    // ========================================================================
    // DTO MAPPERS (FIXED WITH UNIQUE HANDLE)
    // ========================================================================

    private UserProfileResponse mapToProfileResponse(UserProfile profile, int score) {
        UserPreferences prefs = profile.getPreferences();
        UserPreferencesResponse prefsResponse = (prefs != null)
                ? new UserPreferencesResponse(prefs.getTheme(), prefs.isEmailNotifications(), prefs.getProfileVisibility())
                : null;

        List<UserSkillResponse> skills = profile.getSkills().stream()
                .filter(s -> !s.isDeleted())
                .map(s -> new UserSkillResponse(s.getId(), s.getSkillName(), s.getSkillLevel(), s.isVerified(), s.getVerifiedAt()))
                .collect(Collectors.toList());

        List<UserInterestResponse> interests = profile.getInterests().stream()
                .filter(i -> !i.isDeleted())
                .map(i -> new UserInterestResponse(i.getId(), i.getInterestName()))
                .collect(Collectors.toList());

        List<UserSocialLinkResponse> links = profile.getSocialLinks().stream()
                .filter(l -> !l.isDeleted())
                .map(l -> new UserSocialLinkResponse(l.getId(), l.getPlatformName(), l.getUrl()))
                .collect(Collectors.toList());

        // FIXED: Added profile.getUniqueHandle()
        return new UserProfileResponse(
                profile.getId(), profile.getUniqueHandle(), profile.getDisplayName(), profile.getHeadline(), profile.getBio(),
                profile.getLocation(), profile.getCollege(), profile.getExperienceLevel(),
                profile.getProfileImageUrl(), score, profile.getProjectsCompleted(),
                prefsResponse, skills, interests, links
        );
    }

    private PublicProfileResponse mapToPublicProfileResponse(UserProfile profile, int score) {
        List<UserSkillResponse> skills = profile.getSkills().stream()
                .filter(s -> !s.isDeleted())
                .map(s -> new UserSkillResponse(s.getId(), s.getSkillName(), s.getSkillLevel(), s.isVerified(), s.getVerifiedAt()))
                .collect(Collectors.toList());

        List<UserInterestResponse> interests = profile.getInterests().stream()
                .filter(i -> !i.isDeleted())
                .map(i -> new UserInterestResponse(i.getId(), i.getInterestName()))
                .collect(Collectors.toList());

        List<UserSocialLinkResponse> links = profile.getSocialLinks().stream()
                .filter(l -> !l.isDeleted())
                .map(l -> new UserSocialLinkResponse(l.getId(), l.getPlatformName(), l.getUrl()))
                .collect(Collectors.toList());

        // FIXED: Added profile.getUniqueHandle()
        return new PublicProfileResponse(
                profile.getId(), profile.getUniqueHandle(), profile.getDisplayName(), profile.getHeadline(), profile.getBio(),
                profile.getLocation(), profile.getCollege(), profile.getExperienceLevel(),
                profile.getProfileImageUrl(), score, profile.getProjectsCompleted(),
                skills, interests, links
        );
    }
}