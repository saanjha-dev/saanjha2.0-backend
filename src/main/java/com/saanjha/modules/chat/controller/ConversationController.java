package com.saanjha.modules.chat.controller;

import com.saanjha.modules.chat.dto.ChatRequestDTOs.*;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.*;
import com.saanjha.modules.chat.service.ConversationService;
import com.saanjha.shared.api.ApiEnvelope;
import com.saanjha.shared.ratelimit.RateLimit;
import com.saanjha.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST owns Conversation CRUD, settings, and roster/moderation management
 * (module brief's "WEBSOCKET" section reserves send/typing/presence/
 * reactions/read-receipts for the STOMP layer - see {@code
 * ChatWebSocketController} - everything here is a REST mutation, and where
 * a live subscriber should also see the change (lock/unlock, membership
 * changes), the underlying service publishes a domain event that a future
 * broadcast listener can relay - see Future Extension Points).
 *
 * Every mutating/reading endpoint below enforces membership via {@code
 * @chatGuard.isMember(...)} (or the stricter {@code isManager}/{@code
 * isOwner} for administrative actions), never the global {@code
 * chat:participate}/{@code chat:manage} permission alone - the same
 * necessary-but-not-sufficient split TeamSecurityGuard established, applied
 * here from day one rather than retrofitted after an S12-shaped incident.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "11. Chat", description = "Conversations, membership, and moderation")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/v1/chats/conversations")
    @RateLimit(action = "create-conversation", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:participate')")
    @Operation(summary = "Create Conversation", description = "Creates a DIRECT_MESSAGE, GROUP, or SUPPORT conversation. PROJECT_TEAM/PROJECT_ANNOUNCEMENTS/SYSTEM are event-provisioned only.")
    public ResponseEntity<ApiEnvelope<ConversationResponse>> create(@Valid @RequestBody CreateConversationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiEnvelope.success(conversationService.createConversation(userId, request)));
    }

    @GetMapping("/v1/chats/conversations/{id}")
    @RateLimit(action = "get-conversation", baseLimit = 60)
    @PreAuthorize("hasAuthority('chat:moderate') or @chatGuard.isMember(#id, authentication.name)")
    @Operation(summary = "Get Conversation")
    public ResponseEntity<ApiEnvelope<ConversationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.success(conversationService.getConversation(id)));
    }

    @GetMapping("/v1/chats/conversations")
    @RateLimit(action = "list-my-conversations", baseLimit = 30)
    @PreAuthorize("hasAuthority('chat:participate')")
    @Operation(summary = "List My Conversations", description = "All conversations the caller currently belongs to, most recently active first.")
    public ResponseEntity<ApiEnvelope<java.util.List<ConversationSummaryResponse>>> listMine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Page<ConversationSummaryResponse> result = conversationService.listMyConversations(userId, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @GetMapping("/v1/chats/conversations/{id}/members")
    @RateLimit(action = "list-conversation-members", baseLimit = 30)
    @PreAuthorize("hasAuthority('chat:moderate') or @chatGuard.isMember(#id, authentication.name)")
    @Operation(summary = "Display Active Organizational Structure", description = "Paginated live (ACTIVE/MUTED) roster.")
    public ResponseEntity<ApiEnvelope<java.util.List<ConversationMemberResponse>>> listMembers(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Page<ConversationMemberResponse> result = conversationService.listMembers(id, buildPageable(page, size));
        return ResponseEntity.ok(ApiEnvelope.success(result.getContent(), paginationMeta(result)));
    }

    @PatchMapping("/v1/chats/conversations/{id}/settings")
    @RateLimit(action = "update-conversation-settings", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:manage') and @chatGuard.isManager(#id, authentication.name)")
    @Operation(summary = "Update Conversation Settings")
    public ResponseEntity<ApiEnvelope<ConversationResponse>> updateSettings(
            @PathVariable UUID id, @Valid @RequestBody UpdateConversationSettingsRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(conversationService.updateSettings(id, request)));
    }

    @PostMapping("/v1/chats/conversations/{id}/members")
    @RateLimit(action = "add-conversation-member", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:manage') and @chatGuard.isManager(#id, authentication.name)")
    @Operation(summary = "Add Member")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> addMember(@PathVariable UUID id, @Valid @RequestBody AddMemberRequest request) {
        conversationService.addMember(id, request.userId());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Member added.", "OK")));
    }

    @DeleteMapping("/v1/chats/conversations/{id}/members/{userId}")
    @RateLimit(action = "remove-conversation-member", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:manage') and @chatGuard.isManager(#id, authentication.name)")
    @Operation(summary = "Remove an Inactive Collaborator")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> removeMember(
            @PathVariable UUID id, @PathVariable UUID userId, @RequestBody(required = false) RemoveMemberRequest request) {
        UUID actorId = SecurityUtils.getCurrentUserId();
        conversationService.removeMember(id, userId, actorId, request != null ? request.reason() : null);
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Member removed.", "OK")));
    }

    @DeleteMapping("/v1/chats/conversations/{id}/members/me")
    @RateLimit(action = "leave-conversation", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:participate') and @chatGuard.isMember(#id, authentication.name)")
    @Operation(summary = "Leave Conversation")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> leave(@PathVariable UUID id) {
        conversationService.leaveConversation(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Left conversation.", "OK")));
    }

    @PostMapping("/v1/chats/conversations/{id}/members/{userId}/mute")
    @RateLimit(action = "mute-conversation-member", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:manage') and @chatGuard.isManager(#id, authentication.name)")
    @Operation(summary = "Mute Member")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> mute(
            @PathVariable UUID id, @PathVariable UUID userId, @Valid @RequestBody MuteMemberRequest request) {
        UUID actorId = SecurityUtils.getCurrentUserId();
        conversationService.muteMember(id, userId, actorId, request.durationMinutes(), request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Member muted.", "OK")));
    }

    @PostMapping("/v1/chats/conversations/{id}/members/{userId}/block")
    @RateLimit(action = "block-conversation-user", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:manage') and @chatGuard.isManager(#id, authentication.name)")
    @Operation(summary = "Block User")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> block(
            @PathVariable UUID id, @PathVariable UUID userId, @Valid @RequestBody BlockUserRequest request) {
        UUID actorId = SecurityUtils.getCurrentUserId();
        conversationService.blockUser(id, userId, actorId, request.reason());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("User blocked.", "OK")));
    }

    @PostMapping("/v1/chats/conversations/{id}/members/{userId}/unblock")
    @RateLimit(action = "unblock-conversation-user", baseLimit = 20)
    @PreAuthorize("hasAuthority('chat:moderate') or (hasAuthority('chat:manage') and @chatGuard.isOwner(#id, authentication.name))")
    @Operation(summary = "Unblock User", description = "Owner-only (or global moderator) - unblocking is deliberately a higher bar than blocking.")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> unblock(@PathVariable UUID id, @PathVariable UUID userId) {
        conversationService.unblockUser(id, userId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("User unblocked.", "OK")));
    }

    @PostMapping("/v1/chats/conversations/{id}/lock")
    @RateLimit(action = "lock-conversation", baseLimit = 10)
    @PreAuthorize("hasAuthority('chat:moderate') or (hasAuthority('chat:manage') and @chatGuard.isManager(#id, authentication.name))")
    @Operation(summary = "Lock Conversation")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> lock(@PathVariable UUID id, @RequestBody(required = false) LockConversationRequest request) {
        conversationService.lockConversation(id, SecurityUtils.getCurrentUserId(), request != null ? request.reason() : null);
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Conversation locked.", "OK")));
    }

    @PostMapping("/v1/chats/conversations/{id}/unlock")
    @RateLimit(action = "unlock-conversation", baseLimit = 10)
    @PreAuthorize("hasAuthority('chat:moderate') or (hasAuthority('chat:manage') and @chatGuard.isManager(#id, authentication.name))")
    @Operation(summary = "Unlock Conversation")
    public ResponseEntity<ApiEnvelope<ChatMutationResponse>> unlock(@PathVariable UUID id) {
        conversationService.unlockConversation(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiEnvelope.success(new ChatMutationResponse("Conversation unlocked.", "OK")));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Pageable buildPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize);
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
