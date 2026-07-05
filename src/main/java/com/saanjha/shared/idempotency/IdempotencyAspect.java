package com.saanjha.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import com.saanjha.shared.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

/**
 * Backs the {@link Idempotent} annotation with a Redis-based implementation.
 *
 * Lifecycle for a given (action, user, key) tuple:
 *  1. Missing header entirely           -> 400 VALIDATION_FAILED (fail fast, don't guess).
 *  2. Redis has a finished cached reply  -> replay it verbatim, mark the replay header.
 *  3. Redis has the PROCESSING sentinel  -> a concurrent duplicate is in flight -> 409 CONFLICT.
 *  4. Otherwise, atomically claim the key (SETNX) and proceed:
 *       - on success, cache the real response for the configured TTL.
 *       - on failure, evict the sentinel so a legitimate retry isn't poisoned forever.
 *
 * The SETNX claim closes the race window between "check cache" and "run
 * handler" for two identical requests arriving within milliseconds of each
 * other (Spec H.2 #2: Duplicate Applications / replay requests).
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);
    private static final String PROCESSING_SENTINEL = "__PROCESSING__";
    private static final String KEY_PREFIX = "idempotency:";
    private static final String HEADER_NAME = "Idempotency-Key";

    private final StringRedisTemplate redisTemplate;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object enforce(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String suppliedKey = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(suppliedKey)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "The '" + HEADER_NAME + "' header is required for this operation.");
        }

        String scope = resolveScope();
        String redisKey = KEY_PREFIX + idempotent.action() + ":" + scope + ":" + suppliedKey;
        Duration ttl = Duration.ofHours(idempotent.ttlHours());

        String existing = redisTemplate.opsForValue().get(redisKey);
        if (existing != null) {
            if (PROCESSING_SENTINEL.equals(existing)) {
                throw new AppException(ErrorCode.CONFLICT,
                        "An identical request is already being processed. Please wait before retrying.");
            }
            return replay(existing);
        }

        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING_SENTINEL, ttl);
        if (Boolean.FALSE.equals(claimed)) {
            // Lost the race between our GET and SETNX to a concurrent duplicate.
            throw new AppException(ErrorCode.CONFLICT,
                    "An identical request is already being processed. Please wait before retrying.");
        }

        try {
            Object result = joinPoint.proceed();
            cacheResult(redisKey, ttl, result);
            return result;
        } catch (Throwable ex) {
            // Never let a failed attempt permanently block legitimate retries.
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }

    private String resolveScope() {
        try {
            return SecurityUtils.getCurrentUserId().toString();
        } catch (AppException ex) {
            // Unauthenticated context: fall back to remote address so anonymous
            // idempotent endpoints (should any exist in future) don't collide across users.
            return request.getRemoteAddr();
        }
    }

    private void cacheResult(String redisKey, Duration ttl, Object result) {
        if (!(result instanceof ResponseEntity<?> responseEntity)) {
            log.warn("@Idempotent method did not return ResponseEntity; skipping cache for key {}", redisKey);
            return;
        }
        try {
            CachedResponse cached = new CachedResponse(
                    responseEntity.getStatusCode().value(),
                    objectMapper.writeValueAsString(responseEntity.getBody())
            );
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(cached), ttl);
        } catch (Exception ex) {
            log.error("Failed to cache idempotent response for key {}", redisKey, ex);
        }
    }

    private ResponseEntity<Object> replay(String cachedJson) {
        try {
            CachedResponse cached = objectMapper.readValue(cachedJson, CachedResponse.class);
            Object body = objectMapper.readValue(cached.body(), Map.class);
            return ResponseEntity.status(HttpStatus.valueOf(cached.status()))
                    .header("X-Idempotency-Replay", "true")
                    .body(body);
        } catch (Exception ex) {
            log.error("Failed to deserialize cached idempotent response; failing open with 500.", ex);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to replay cached response.");
        }
    }

    private record CachedResponse(int status, String body) {}
}
