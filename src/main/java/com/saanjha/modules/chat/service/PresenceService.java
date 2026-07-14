package com.saanjha.modules.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Presence is deliberately NOT a Postgres table (module brief: "Do not
 * persist unnecessary transient state"). It lives entirely in Redis with a
 * TTL, following the exact convention {@code PermissionCacheService} already
 * established for ephemeral per-user state in this codebase - a value that
 * is correct to simply disappear on expiry/restart, never something a
 * migration or backup needs to protect.
 *
 * A heartbeat (sent periodically over the same WebSocket session, see
 * {@code ChatWebSocketController#heartbeat}) refreshes the TTL; if it stops
 * arriving (tab closed, network drop, no clean DISCONNECT frame), presence
 * naturally expires to OFFLINE without any explicit cleanup job.
 *
 * SESSION COUNTING (fix, hardening sprint): a user's presence key must not
 * go OFFLINE just because *one* of their STOMP sessions disconnected - the
 * same user can have several live sessions at once (multiple browser tabs,
 * phone + laptop, a reconnect racing the old socket's close). A session
 * reference count is tracked alongside the status so DISCONNECT only clears
 * presence once the *last* live session for that user ends. {@link
 * com.saanjha.modules.chat.websocket.ChatChannelInterceptor} calls {@link
 * #sessionConnected} on CONNECT and {@link #sessionDisconnected} on
 * DISCONNECT - never {@code setStatus}/{@code clear} directly for those two
 * lifecycle events, since those bypass the count.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_KEY_PREFIX = "chat:presence:";
    private static final String SESSION_COUNT_KEY_PREFIX = "chat:presence:sessions:";
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(90);

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Call exactly once per STOMP CONNECT. Increments the user's live-session
     * count and ensures the status is ONLINE. Safe to call concurrently for
     * the same user from different sessions (INCR is atomic in Redis).
     */
    public void sessionConnected(UUID userId) {
        Long count = redisTemplate.opsForValue().increment(sessionCountKey(userId));
        redisTemplate.expire(sessionCountKey(userId), PRESENCE_TTL);
        if (count == null || count <= 1) {
            // First live session for this user - actually transition to ONLINE.
            writeStatus(userId, "ONLINE");
        } else {
            // Already had a live session; just keep the TTL fresh.
            redisTemplate.expire(presenceKey(userId), PRESENCE_TTL);
        }
    }

    /**
     * Call exactly once per STOMP DISCONNECT for a session that previously
     * called {@link #sessionConnected}. Only clears presence once the count
     * reaches zero, so other live sessions for the same user are unaffected.
     */
    public void sessionDisconnected(UUID userId) {
        Long remaining = redisTemplate.opsForValue().decrement(sessionCountKey(userId));
        if (remaining == null || remaining <= 0) {
            redisTemplate.delete(sessionCountKey(userId));
            redisTemplate.delete(presenceKey(userId));
        }
    }

    /**
     * Explicit status change (e.g. user sets themselves to AWAY/BUSY) from an
     * already-connected session. Does not touch the session count, and does
     * not resurrect a user with zero live sessions.
     */
    public void setStatus(UUID userId, String status) {
        if (isConnected(userId)) {
            writeStatus(userId, status);
        }
    }

    /** Called on every heartbeat/activity frame - refreshes TTL without changing the stored status. */
    public void heartbeat(UUID userId) {
        if (isConnected(userId)) {
            redisTemplate.expire(presenceKey(userId), PRESENCE_TTL);
            redisTemplate.expire(sessionCountKey(userId), PRESENCE_TTL);
        } else {
            // No tracked session (e.g. count key expired from a lost DISCONNECT) -
            // treat this heartbeat as re-establishing one live session.
            sessionConnected(userId);
        }
    }

    /** Unconditional clear, bypassing the session count. Reserved for admin/moderation use, not the CONNECT/DISCONNECT lifecycle. */
    public void clear(UUID userId) {
        redisTemplate.delete(sessionCountKey(userId));
        redisTemplate.delete(presenceKey(userId));
    }

    /** Returns "OFFLINE" for any user with no live TTL entry - absence IS the offline signal, by design. */
    public String getStatus(UUID userId) {
        String raw = redisTemplate.opsForValue().get(presenceKey(userId));
        if (raw == null) {
            return "OFFLINE";
        }
        return raw.split("\\|")[0];
    }

    private boolean isConnected(UUID userId) {
        String raw = redisTemplate.opsForValue().get(sessionCountKey(userId));
        if (raw == null) {
            return false;
        }
        try {
            return Long.parseLong(raw) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void writeStatus(UUID userId, String status) {
        redisTemplate.opsForValue().set(presenceKey(userId), status + "|" + Instant.now().toEpochMilli(), PRESENCE_TTL);
    }

    private String presenceKey(UUID userId) {
        return PRESENCE_KEY_PREFIX + userId;
    }

    private String sessionCountKey(UUID userId) {
        return SESSION_COUNT_KEY_PREFIX + userId;
    }
}
