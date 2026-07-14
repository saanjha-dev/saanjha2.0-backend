package com.saanjha.modules.chat.websocket;

import com.saanjha.modules.auth.service.JwtProvider;
import com.saanjha.modules.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Authenticates the STOMP CONNECT frame using the same RS256 JWT the REST
 * API accepts (Authorization: Bearer &lt;token&gt; header on the frame, not
 * the HTTP upgrade request - browsers can't attach custom headers to a raw
 * WebSocket handshake, so the client's STOMP library sends it as a STOMP
 * header on CONNECT instead). A session with no valid token never reaches a
 * Principal, so any {@code @MessageMapping} handler downstream can safely
 * assume {@code Principal.getName()} is a validated user id.
 *
 * Deliberately reuses {@link JwtProvider#validateAndGetUserId(String)} - the
 * exact same validation path the REST {@code JwtAuthenticationFilter} uses -
 * rather than re-implementing signature verification here, so RS256
 * key-rotation or algorithm changes only ever need to happen in one place.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatChannelInterceptor implements ChannelInterceptor {

    private final JwtProvider jwtProvider;
    private final PresenceService presenceService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = extractBearerToken(authHeader);
            if (token == null) {
                log.warn("Chat WebSocket CONNECT rejected: no Bearer token presented.");
                throw new org.springframework.messaging.MessagingException("Missing or malformed Authorization header.");
            }
            UUID userId;
            try {
                userId = jwtProvider.validateAndGetUserId(token);
            } catch (Exception ex) {
                log.warn("Chat WebSocket CONNECT rejected: invalid token ({}).", ex.getMessage());
                throw new org.springframework.messaging.MessagingException("Invalid or expired token.");
            }
            // Auth propagation for STOMP frames uses accessor.setUser() ONLY.
            // SecurityContextHolder is intentionally never touched here: it's
            // ThreadLocal-backed, and Spring's clientInboundChannel dispatches
            // frames (including later SEND/SUBSCRIBE frames for this same
            // session) on pooled threads that are shared and reused across
            // *different* users' sessions. A ThreadLocal write here would
            // never be reliably cleared and could leak one user's identity
            // into whatever runs next on that pooled thread. Every downstream
            // @MessageMapping handler in ChatWebSocketController resolves the
            // caller via the Principal method argument (backed by
            // accessor.getUser()), which Spring Messaging propagates
            // correctly per-message regardless of thread - never fall back to
            // SecurityContextHolder/SecurityUtils.getCurrentUserId() inside a
            // STOMP handler or any service it calls.
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            accessor.setUser(authentication);
            // sessionConnected (not setStatus) - this session is one of
            // potentially several concurrent sessions for this user; presence
            // must only clear once ALL of them disconnect. See PresenceService.
            presenceService.sessionConnected(userId);
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            if (accessor.getUser() != null) {
                UUID userId = UUID.fromString(accessor.getUser().getName());
                // sessionDisconnected (not clear) - only marks the user OFFLINE
                // once this was their last live session (multi-tab/multi-device safe).
                presenceService.sessionDisconnected(userId);
            }
        }
        return message;
    }

    /**
     * Extracts the JWT from the Authorization header.
     * @param header The Authorization header string
     * @return The extracted token, or null if missing or malformed
     */
    private String extractBearerToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
