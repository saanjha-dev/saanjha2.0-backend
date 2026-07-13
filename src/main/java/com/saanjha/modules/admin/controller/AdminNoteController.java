package com.saanjha.modules.admin.controller;

import com.saanjha.modules.admin.dto.AdminRequestDTOs.CreateNoteRequest;
import com.saanjha.modules.admin.dto.AdminResponseDTOs.AdminNoteResponse;
import com.saanjha.modules.admin.entity.AdminNote;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.service.AdminNoteService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/notes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "21. Admin - Investigation Notes")
@PreAuthorize("hasAuthority('admin:moderate')")
public class AdminNoteController {

    private final AdminNoteService noteService;

    @PostMapping
    @Operation(summary = "Add Internal Note", description = "Never shown to the target user.")
    public ResponseEntity<ApiEnvelope<AdminNoteResponse>> addNote(@Valid @RequestBody CreateNoteRequest request) {
        AdminNote note = noteService.addNote(SecurityUtils.getCurrentUserId(), request.targetType(), request.targetId(), request.note());
        return ResponseEntity.ok(ApiEnvelope.success(toResponse(note)));
    }

    @GetMapping("/{targetType}/{targetId}")
    @Operation(summary = "Get Notes for a Target")
    public ResponseEntity<ApiEnvelope<List<AdminNoteResponse>>> getNotes(@PathVariable ModerationTargetType targetType, @PathVariable UUID targetId) {
        List<AdminNoteResponse> notes = noteService.getNotes(targetType, targetId).stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiEnvelope.success(notes));
    }

    private AdminNoteResponse toResponse(AdminNote n) {
        return new AdminNoteResponse(n.getId(), n.getTargetType().name(), n.getTargetId(), n.getAuthorId(), n.getNote(), n.getCreatedAt());
    }
}
