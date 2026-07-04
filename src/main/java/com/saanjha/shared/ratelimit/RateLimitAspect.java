package com.saanjha.shared.ratelimit;

import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;
    private final HttpServletRequest request;

    @Around("@annotation(rateLimitAnnotation)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimitAnnotation) throws Throwable {

        String identifier = getClientIdentifier();
        // Fallback to method name if action string isn't provided
        String actionName = rateLimitAnnotation.action().isEmpty() ? joinPoint.getSignature().getName() : rateLimitAnnotation.action();
        String redisKey = "rate_limit:" + actionName + ":" + identifier;

        Long currentCount = redisTemplate.opsForValue().increment(redisKey);

        // First attempt ever? Set the initial time window
        if (currentCount != null && currentCount == 1L) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(rateLimitAnnotation.baseTimeSeconds()));
        }

        // Did they cross the limit? Apply Exponential Backoff
        if (currentCount != null && currentCount > rateLimitAnnotation.baseLimit()) {
            long strikes = currentCount - rateLimitAnnotation.baseLimit();
            long penaltyMultiplier = (long) Math.pow(2, strikes - 1);
            long exponentialPenalty = rateLimitAnnotation.baseTimeSeconds() * penaltyMultiplier;

            // Overwrite the Redis TTL with the new, longer penalty
            redisTemplate.expire(redisKey, Duration.ofSeconds(exponentialPenalty));

            throw new AppException(
                    ErrorCode.TOO_MANY_REQUESTS, // Ensure this maps to HTTP 429 in your GlobalExceptionHandler
                    rateLimitAnnotation.errorMessage() + ". Please try after " + exponentialPenalty + " seconds."
            );
        }

        return joinPoint.proceed();
    }

    private String getClientIdentifier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName(); // Returns the Auth UUID from the JWT token
        }
        
        // Fallback: X-Forwarded-For to bypass reverse proxies, else raw IP
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}