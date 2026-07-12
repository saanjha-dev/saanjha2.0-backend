package com.saanjha.modules.chat.controller;

import com.saanjha.modules.chat.dto.ChatRequestDTOs.SaveDraftRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.ChatMutationResponse;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.DraftResponse;
import com.saanjha.modules.chat.entity.Draft;
import com.saanjha.modules.chat.service.DraftService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "11. Chat", description = "Per-user message drafts")
public class DraftController {

    private final DraftService draftService;

    @PutMapping("/v1/chats/conversations/{conversationId}/draft")
    @RateLimit(action = "save-draft", baseLimit = 60)
    @PreAuthorize("hasAuthority('chat:participate') and @chatGuard.isMember(#conversationId, authentication.name)")
    @Operation(summary = "Save Draft", description = "Upsert - one top-level draft and one per thread, per user, per conversation.")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> save(
            @PathVariable UUID conversationId, @Valid @RequestBody SaveDraftRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        draftService.save(conversationId, userId, request.parentMessageId(), request.body());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Draft saved.", "OK")));
    }

    @GetMapping("/v1/chats/conversations/{conversationId}/draft")
    @RateLimit(action = "get-draft", baseLimit = 60)
    @PreAuthorize("hasAuthority('chat:participate') and @chatGuard.isMember(#conversationId, authentication.name)")
    @Operation(summary = "Get Draft")
    public ResponseEntity<ApiEnvelope<DraftResponse>> get(
            @PathVariable UUID conversationId, @RequestParam(required = false) UUID parentMessageId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Optional<Draft> draft = draftService.get(conversationId, userId, parentMessageId);
        DraftResponse response = draft.map(d -> new DraftResponse(d.getConversationId(), d.getParentMessageId(), d.getBody(), d.getUpdatedAt()))
                .orElse(new DraftResponse(conversationId, parentMessageId, null, null));
        return ResponseEntity.ok(ApiEnvelope.success(response));
    }

    @DeleteMapping("/v1/chats/conversations/{conversationId}/draft")
    @RateLimit(action = "clear-draft", baseLimit = 60)
    @PreAuthorize("hasAuthority('chat:participate') and @chatGuard.isMember(#conversationId, authentication.name)")
    @Operation(summary = "Clear Draft")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> clear(
            @PathVariable UUID conversationId, @RequestParam(required = false) UUID parentMessageId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        draftService.clear(conversationId, userId, parentMessageId);
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Draft cleared.", "OK")));
    }
}
