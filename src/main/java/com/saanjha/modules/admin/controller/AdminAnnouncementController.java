package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.CreateAnnouncementRequest;
import com.saanjha.modules.admin.dto.AdminResponseDTOs.AnnouncementResponse;
import com.saanjha.modules.admin.entity.Announcement;
import com.saanjha.modules.admin.service.AnnouncementService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "18. Admin - Announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    /** Public-facing: any client (including guests) can render the current banner set. */
    @GetMapping("/v1/announcements/live")
    @Operation(summary = "Currently Live Announcements", description = "Public — no authentication required, matches GUEST access to public projects.")
    public ResponseEntity<ApiEnvelope<List<AnnouncementResponse>>> live() {
        List<AnnouncementResponse> announcements = announcementService.getLiveAnnouncements().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiEnvelope.success(announcements));
    }

    @GetMapping("/v1/admin/announcements")
    @PreAuthorize("hasAuthority('admin:announce')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List All Announcements")
    public ResponseEntity<ApiEnvelope<Page<AnnouncementResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiEnvelope.success(announcementService.listAll(pageable).map(this::toResponse)));
    }

    @PostMapping("/v1/admin/announcements")
    @PreAuthorize("hasAuthority('admin:announce')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create Announcement (immediate or scheduled)")
    public ResponseEntity<ApiEnvelope<AnnouncementResponse>> create(@Valid @RequestBody CreateAnnouncementRequest request) {
        Announcement announcement = announcementService.create(
                SecurityUtils.getCurrentUserId(), request.title(), request.body(), request.type(),
                request.audience(), request.priority(), request.startsAt(), request.expiresAt());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(announcement)));
    }

    @PostMapping("/v1/admin/announcements/{id}/cancel")
    @PreAuthorize("hasAuthority('admin:announce')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancel a Draft or Scheduled Announcement")
    public ResponseEntity<ApiEnvelope<Void>> cancel(@PathVariable UUID id) {
        announcementService.cancel(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    @PostMapping("/v1/admin/announcements/{id}/unpublish")
    @PreAuthorize("hasAuthority('admin:announce')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Withdraw a Live Announcement")
    public ResponseEntity<ApiEnvelope<Void>> unpublish(@PathVariable UUID id) {
        announcementService.unpublish(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiEnvelope.success(null));
    }

    private AnnouncementResponse toResponse(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), a.getType().name(), a.getAudience().name(),
                a.getPriority().name(), a.getStatus().name(), a.getStartsAt(), a.getExpiresAt(), a.getPublishedAt(), a.getCreatedAt());
    }
}
