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
 * {@code ChatWebSocketController#onHeartbeat}) refreshes the TTL; if it stops
 * arriving (tab closed, network drop, no clean DISCONNECT frame), presence
 * naturally expires to OFFLINE without any explicit cleanup job.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_KEY_PREFIX = "chat:presence:";
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(90);

    private final RedisTemplate<String, String> redisTemplate;

    public void setStatus(UUID userId, String status) {
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, status + "|" + Instant.now().toEpochMilli(), PRESENCE_TTL);
    }

    /** Called on every heartbeat/activity frame - refreshes TTL without changing the stored status. */
    public void heartbeat(UUID userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.expire(key, PRESENCE_TTL);
        } else {
            setStatus(userId, "ONLINE");
        }
    }

    public void clear(UUID userId) {
        redisTemplate.delete(PRESENCE_KEY_PREFIX + userId);
    }

    /** Returns "OFFLINE" for any user with no live TTL entry - absence IS the offline signal, by design. */
    public String getStatus(UUID userId) {
        String raw = redisTemplate.opsForValue().get(PRESENCE_KEY_PREFIX + userId);
        if (raw == null) {
            return "OFFLINE";
        }
        return raw.split("\\|")[0];
    }
}
