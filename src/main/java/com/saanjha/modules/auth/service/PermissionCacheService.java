package com.saanjha.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    private static final String CACHE_PREFIX = "auth:permissions:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public Set<String> getUserAuthorities(UUID userId) {
        String key = CACHE_PREFIX + userId;
        
        Set<String> cachedPermissions = redisTemplate.opsForSet().members(key);
        if (cachedPermissions != null && !cachedPermissions.isEmpty()) {
            return cachedPermissions;
        }

        // Cache Miss: Query the DB using native SQL for an ultra-fast relational join
        String sql = """
            SELECT DISTINCT p.name 
            FROM auth.auth_permissions p
            JOIN auth.auth_role_permissions rp ON p.id = rp.permission_id
            JOIN auth.auth_user_roles ur ON rp.role_id = ur.role_id
            WHERE ur.user_id = ?
        """;

        List<String> dbPermissions = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("name"), userId);
        Set<String> authorities = new HashSet<>(dbPermissions);

        if (!authorities.isEmpty()) {
            redisTemplate.opsForSet().add(key, authorities.toArray(new String[0]));
            redisTemplate.expire(key, CACHE_TTL);
        }

        return authorities;
    }

    public void evictUserCache(UUID userId) {
        redisTemplate.delete(CACHE_PREFIX + userId);
    }
}