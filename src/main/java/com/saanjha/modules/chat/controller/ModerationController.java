package com.saanjha.modules.chat.controller;

import com.saanjha.modules.chat.dto.ChatResponseDTOs.ModerationActionResponse;
import com.saanjha.modules.chat.entity.ModerationAction;
import com.saanjha.modules.chat.repository.ModerationActionRepository;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only audit trail over {@code cht_moderation_actions}. Deliberately
 * restricted to managers/moderators only - this surface includes free-text
 * removal/mute reasons, the same class of sensitive operational detail
 * TD25/S13 flagged for Team's membership history, so it does not get the
 * plain {@code isMember} visibility exception {@code ConversationController}
 * gives ordinary reads.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "11. Chat", description = "Moderation action audit trail")
public class ModerationController {

    private final ModerationActionRepository moderationActionRepository;

    @GetMapping("/v1/chats/conversations/{conversationId}/moderation-actions")
    @RateLimit(action = "list-moderation-actions", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:moderate') or (hasAuthority('chat:manage') and @chatGuard.isManager(#conversationId, authentication.name))")
    @Operation(summary = "Moderation Action History")
    public ResponseEntity<ApiEnvelope<List<ModerationActionResponse>>> list(
            @PathVariable UUID conversationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<ModerationAction> result = moderationActionRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
        Page<ModerationActionResponse> mapped = result.map(a -> new ModerationActionResponse(
                a.getId(), a.getActionType().name(), a.getTargetUserId(), a.getMessageId(), a.getReason(), a.getActorId(), a.getCreatedAt()));
        Map<String, Object> meta = new HashMap<>();
        meta.put("page", mapped.getNumber());
        meta.put("size", mapped.getSize());
        meta.put("totalElements", mapped.getTotalElements());
        meta.put("totalPages", mapped.getTotalPages());
        return ResponseEntity.ok(ApiEnvelope.success(mapped.getContent(), meta));
    }
}
