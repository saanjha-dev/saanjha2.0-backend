package com.saanjha.modules.user.controller;

import com.saanjha.modules.user.dto.UserRequestDTOs.*;
import com.saanjha.modules.user.dto.UserResponseDTOs.*;
import com.saanjha.modules.user.service.UserProfileService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
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
    @Operation(summary = "Update My Profile", description = "Updates core profile fields (bio, headline, location) and returns the updated score.")
    public ResponseEntity<ApiEnvelope<ProfileMutationResponse>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        int updatedScore = profileService.updateProfile(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success(
                new ProfileMutationResponse("Profile updated successfully.", updatedScore)
        ));
    }

    // ========================================================================
    // PUBLIC PROFILE ENDPOINT
    // ========================================================================

    @GetMapping("/{userId}")
    @RateLimit(action = "get-public-profile", baseLimit = 30)
    @Operation(summary = "Get Public Profile", description = "Fetches a user's profile. Strictly enforces visibility preferences (Public, Private, Connections Only).")
    public ResponseEntity<ApiEnvelope<PublicProfileResponse>> getPublicProfile(@PathVariable UUID userId) {
        PublicProfileResponse response = profileService.getPublicProfile(userId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

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

        // Wrapped the URL in a proper DTO and use the single-parameter success method
        return ResponseEntity.ok(ApiEnvelope.success(new ProfilePhotoResponse(newUrl)));
    }

    // ========================================================================
    // PUBLIC SLUG ROUTING
    // ========================================================================

    @GetMapping("/handle/{handle}")
    @RateLimit(action = "get-profile-by-handle", baseLimit = 30)
    @Operation(summary = "Get Profile by Handle", description = "Fetches a public profile via vanity slug (e.g., '@rahul_dev'). Case-insensitive.")
    public ResponseEntity<ApiEnvelope<PublicProfileResponse>> getProfileByHandle(@PathVariable String handle) {
        PublicProfileResponse response = profileService.getProfileByHandle(handle);
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    // ========================================================================
    // GDPR DATA COMPLIANCE EXPORT
    // ========================================================================

    @GetMapping("/me/export")
    @RateLimit(action = "gdpr-export", baseLimit = 2, baseTimeSeconds = 3600) // Restricted to 2 attempts per hour
    @Operation(summary = "Export Personal Data", description = "Generates and downloads a complete structural JSON file containing all data stored about the user, complying with legal GDPR right-to-data-portability parameters.")
    public ResponseEntity<byte[]> exportMyData() {
        UserProfileResponse dataSnapshot = profileService.exportUserData(SecurityUtils.getCurrentUserId());

        // Convert our standard object into an indented, beautiful JSON byte array payload
        byte[] jsonBytes;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()); // Correctly formats timestamps
            jsonBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(dataSnapshot);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to compile data archive.");
        }

        String filename = "saanjha_data_archive_" + SecurityUtils.getCurrentUserId() + ".json";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(jsonBytes);
    }


    // ========================================================================
    // SKILLS ENDPOINTS
    // ========================================================================

    @PostMapping("/me/skills")
    @RateLimit(action = "add-skill", baseLimit = 15)
    @Operation(summary = "Add a Skill", description = "Adds a new skill to the user's profile and returns the new score.")
    public ResponseEntity<ApiEnvelope<ProfileMutationResponse>> addSkill(@Valid @RequestBody AddSkillRequest request) {
        int updatedScore = profileService.addSkill(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success(
                new ProfileMutationResponse("Skill added successfully.", updatedScore)
        ));
    }

    @DeleteMapping("/me/skills/{skillId}")
    @RateLimit(action = "delete-skill", baseLimit = 15)
    @Operation(summary = "Remove a Skill", description = "Permanently removes a skill from the user's profile and returns the new score.")
    public ResponseEntity<ApiEnvelope<ProfileMutationResponse>> removeSkill(@PathVariable UUID skillId) {
        int updatedScore = profileService.removeSkill(SecurityUtils.getCurrentUserId(), skillId);
        return ResponseEntity.ok(ApiEnvelope.success(
                new ProfileMutationResponse("Skill removed.", updatedScore)
        ));
    }

    // ========================================================================
    // INTERESTS ENDPOINTS
    // ========================================================================

    @PostMapping("/me/interests")
    @RateLimit(action = "add-interest", baseLimit = 15)
    @Operation(summary = "Add an Interest", description = "Adds a professional interest and returns the new score.")
    public ResponseEntity<ApiEnvelope<ProfileMutationResponse>> addInterest(@Valid @RequestBody AddInterestRequest request) {
        int updatedScore = profileService.addInterest(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success(
                new ProfileMutationResponse("Interest added successfully.", updatedScore)
        ));
    }

    @DeleteMapping("/me/interests/{interestId}")
    @RateLimit(action = "delete-interest", baseLimit = 15)
    @Operation(summary = "Remove an Interest", description = "Permanently removes an interest and returns the new score.")
    public ResponseEntity<ApiEnvelope<ProfileMutationResponse>> removeInterest(@PathVariable UUID interestId) {
        int updatedScore = profileService.removeInterest(SecurityUtils.getCurrentUserId(), interestId);
        return ResponseEntity.ok(ApiEnvelope.success(
                new ProfileMutationResponse("Interest removed.", updatedScore)
        ));
    }

    // ========================================================================
    // SOCIAL LINKS ENDPOINTS
    // ========================================================================

    @PostMapping("/me/social-links")
    @RateLimit(action = "add-social-link", baseLimit = 10)
    @Operation(summary = "Add a Social Link", description = "Links external profiles and returns the new score.")
    public ResponseEntity<ApiEnvelope<ProfileMutationResponse>> addSocialLink(@Valid @RequestBody AddSocialLinkRequest request) {
        int updatedScore = profileService.addSocialLink(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiEnvelope.success(
                new ProfileMutationResponse("Social link added successfully.", updatedScore)
        ));
    }

    @DeleteMapping("/me/social-links/{linkId}")
    @RateLimit(action = "delete-social-link", baseLimit = 10)
    @Operation(summary = "Remove a Social Link", description = "Permanently removes an external link and returns the new score.")
    public ResponseEntity<ApiEnvelope<ProfileMutationResponse>> removeSocialLink(@PathVariable UUID linkId) {
        int updatedScore = profileService.removeSocialLink(SecurityUtils.getCurrentUserId(), linkId);
        return ResponseEntity.ok(ApiEnvelope.success(
                new ProfileMutationResponse("Social link removed.", updatedScore)
        ));
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