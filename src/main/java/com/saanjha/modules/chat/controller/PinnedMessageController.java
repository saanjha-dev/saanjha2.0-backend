package com.saanjha.modules.chat.controller;

import com.saanjha.modules.chat.dto.ChatResponseDTOs.ChatMutationResponse;
import com.saanjha.modules.chat.entity.PinnedMessage;
import com.saanjha.modules.chat.service.PinnedMessageService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
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

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "11. Chat", description = "Pinned messages")
public class PinnedMessageController {

    private final PinnedMessageService pinnedMessageService;

    @PostMapping("/v1/chats/conversations/{conversationId}/pins/{messageId}")
    @RateLimit(action = "pin-message", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:manage') and @chatGuard.isManager(#conversationId, authentication.name)")
    @Operation(summary = "Pin Message")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> pin(@PathVariable UUID conversationId, @PathVariable UUID messageId) {
        pinnedMessageService.pin(conversationId, messageId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Message pinned.", "OK")));
    }

    @DeleteMapping("/v1/chats/conversations/{conversationId}/pins/{messageId}")
    @RateLimit(action = "unpin-message", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:manage') and @chatGuard.isManager(#conversationId, authentication.name)")
    @Operation(summary = "Unpin Message")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> unpin(@PathVariable UUID conversationId, @PathVariable UUID messageId) {
        pinnedMessageService.unpin(conversationId, messageId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Message unpinned.", "OK")));
    }

    @GetMapping("/v1/chats/conversations/{conversationId}/pins")
    @RateLimit(action = "list-pins", baseLimit = 30)
    @PreAuthorize("hasAuthority('chat:moderate') or @chatGuard.isMember(#conversationId, authentication.name)")
    @Operation(summary = "Pinned Message History")
    public ResponseEntity<ApiEnvelope<List<PinnedMessage>>> list(
            @PathVariable UUID conversationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<PinnedMessage> result = pinnedMessageService.listActive(conversationId, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    private Pageable buildPageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }

    private Map<String, Object> paginationMeta(Page<?> page) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("page", page.getNumber());
        meta.put("size", page.getSize());
        meta.put("totalElements", page.getTotalElements());
        meta.put("totalPages", page.getTotalPages());
        return meta;
    }
}
