package com.saanjha.modules.user.controller;

import com.saanjha.modules.user.dto.UserRequestDTOs.*;
import com.saanjha.modules.user.dto.UserResponseDTOs.*;
import com.saanjha.modules.user.service.UserProfileService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth") // Applies to all endpoints in this controller
@Tag(name = "2. User Profile", description = "Identity metadata, skills, social links, and preferences")
public class UserController {

    private final UserProfileService profileService;

    // ========================================================================
    // PROFILE ENDPOINTS
    // ========================================================================

    @GetMapping("/me")
    @RateLimit(action = "get-profile", baseLimit = 20)
    @Operation(summary = "Get My Profile", description = "Fetches the authenticated user's complete profile, calculated score, and sub-entities.")
    public ResponseEntity<ApiEnvelope<UserProfileResponse>> getMyProfile() {
        UserProfileResponse response = profileService.getProfile(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @PutMapping("/me")
    @RateLimit(action = "update-profile", baseLimit = 10)
    @Operation(summary = "Update My Profile", description = "Updates core profile fields (bio, headline, location) and triggers a score recalculation.")
    public ResponseEntity<ApiEnvelope<String>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        profileService.updateProfile(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success("Profile updated successfully."));
    }

    // ========================================================================
    // PROFILE PHOTO ENDPOINT
    // ========================================================================

    // ========================================================================
    // PROFILE PHOTO ENDPOINT
    // ========================================================================

    @PostMapping(value = "/me/profile-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(action = "upload-profile-image", baseLimit = 5, baseTimeSeconds = 300)
    @Operation(summary = "Upload or Update Profile Photo", description = "Uploads an image to Cloudinary. Automatically overwrites the existing photo if one exists.")
    public ResponseEntity<ApiEnvelope<ProfilePhotoResponse>> uploadProfilePhoto(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        String newUrl = profileService.uploadProfilePhoto(SecurityUtils.getCurrentUserId(), file);

        // FIXED: Wrap the URL in a proper DTO and use the single-parameter success method
        return ResponseEntity.ok(ApiEnvelope.success(new ProfilePhotoResponse(newUrl)));
    }


    // ========================================================================
    // SKILLS ENDPOINTS
    // ========================================================================

    @PostMapping("/me/skills")
    @RateLimit(action = "add-skill", baseLimit = 15)
    @Operation(summary = "Add a Skill", description = "Adds a new skill to the user's profile. Fails if the skill already exists.")
    public ResponseEntity<ApiEnvelope<String>> addSkill(@Valid @RequestBody AddSkillRequest request) {
        profileService.addSkill(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success("Skill added successfully."));
    }

    @DeleteMapping("/me/skills/{skillId}")
    @RateLimit(action = "delete-skill", baseLimit = 15)
    @Operation(summary = "Remove a Skill", description = "Permanently removes a skill from the user's profile.")
    public ResponseEntity<ApiEnvelope<String>> removeSkill(@PathVariable UUID skillId) {
        profileService.removeSkill(SecurityUtils.getCurrentUserId(), skillId);
        return ResponseEntity.ok(ApiEnvelope.success("Skill removed."));
    }

    // ========================================================================
    // INTERESTS ENDPOINTS
    // ========================================================================

    @PostMapping("/me/interests")
    @RateLimit(action = "add-interest", baseLimit = 15)
    @Operation(summary = "Add an Interest", description = "Adds a professional interest (e.g., 'AI', 'FinTech').")
    public ResponseEntity<ApiEnvelope<String>> addInterest(@Valid @RequestBody AddInterestRequest request) {
        profileService.addInterest(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success("Interest added successfully."));
    }

    @DeleteMapping("/me/interests/{interestId}")
    @RateLimit(action = "delete-interest", baseLimit = 15)
    @Operation(summary = "Remove an Interest", description = "Permanently removes an interest.")
    public ResponseEntity<ApiEnvelope<String>> removeInterest(@PathVariable UUID interestId) {
        profileService.removeInterest(SecurityUtils.getCurrentUserId(), interestId);
        return ResponseEntity.ok(ApiEnvelope.success("Interest removed."));
    }

    // ========================================================================
    // SOCIAL LINKS ENDPOINTS
    // ========================================================================

    @PostMapping("/me/social-links")
    @RateLimit(action = "add-social-link", baseLimit = 10)
    @Operation(summary = "Add a Social Link", description = "Links external profiles. Restricted to one URL per platform (e.g., only one GitHub link).")
    public ResponseEntity<ApiEnvelope<String>> addSocialLink(@Valid @RequestBody AddSocialLinkRequest request) {
        profileService.addSocialLink(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success("Social link added successfully."));
    }

    @DeleteMapping("/me/social-links/{linkId}")
    @RateLimit(action = "delete-social-link", baseLimit = 10)
    @Operation(summary = "Remove a Social Link", description = "Permanently removes an external link.")
    public ResponseEntity<ApiEnvelope<String>> removeSocialLink(@PathVariable UUID linkId) {
        profileService.removeSocialLink(SecurityUtils.getCurrentUserId(), linkId);
        return ResponseEntity.ok(ApiEnvelope.success("Social link removed."));
    }

    // ========================================================================
    // PREFERENCES ENDPOINTS
    // ========================================================================

    @PutMapping("/me/preferences")
    @RateLimit(action = "update-preferences", baseLimit = 10)
    @Operation(summary = "Update Preferences", description = "Updates platform theme, email notifications, and visibility rules.")
    public ResponseEntity<ApiEnvelope<String>> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        profileService.updatePreferences(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success("Preferences updated successfully."));
    }

    @DeleteMapping("/me")
    @RateLimit(action = "delete-account", baseLimit = 3)
    @Operation(summary = "Delete Account", description = "Soft deletes the user profile and triggers session revocation.")
    public ResponseEntity<ApiEnvelope<String>> deleteMyAccount() {
        profileService.softDeleteProfile(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success("Account successfully deleted."));
    }
}