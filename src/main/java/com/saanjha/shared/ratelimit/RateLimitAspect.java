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

    /**
     * FIX (hardening sprint, P0-3, CRITICAL): this previously trusted the
     * raw client-supplied {@code X-Forwarded-For} header with no validation
     * of who sent it. Confirmed via repository-wide search: there is no
     * reverse proxy config in this repo (no nginx/Tomcat RemoteIpValve
     * config, no {@code server.forward-headers-strategy}), so nothing
     * upstream of this application was overwriting that header - any caller
     * could set {@code X-Forwarded-For: <anything>} directly and receive a
     * brand-new rate-limit bucket on literally every request, making every
     * {@code @RateLimit}-protected endpoint in the entire application
     * (login, OTP verification, password reset, registration - not just
     * auth) trivially bypassable. This now uses only
     * {@code request.getRemoteAddr()}, the actual TCP peer address, which
     * cannot be forged at the application layer.
     * <p>
     * If/when this app is deployed behind a real reverse proxy or load
     * balancer, {@code getRemoteAddr()} will report the proxy's own IP for
     * every request instead of the true client IP, collapsing rate limits
     * across all users. The correct fix at that point is Tomcat's
     * RemoteIpValve, configured via {@code server.tomcat.remoteip.*}
     * properties restricted to that proxy's actual IP range (so the header
     * is trusted ONLY when it genuinely comes from that trusted hop) - NOT
     * reverting to trusting X-Forwarded-For unconditionally as before. See
     * {@code docker-compose.yml}/deployment docs for whether a proxy sits in
     * front of this service.
     */
    private String getClientIdentifier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName(); // Returns the Auth UUID from the JWT token
        }

        return request.getRemoteAddr();
    }
}