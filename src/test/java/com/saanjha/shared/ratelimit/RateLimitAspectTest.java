package com.saanjha.shared.ratelimit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIX (hardening sprint, P0-3, CRITICAL): before this fix, {@code
 * getClientIdentifier()} trusted the raw, client-supplied
 * {@code X-Forwarded-For} header with no validation - and this repository
 * has no reverse proxy anywhere in front of the app (confirmed via
 * repository-wide search), so nothing was actually rewriting that header.
 * Any anonymous caller could set a different fake value on every request
 * and get a brand-new rate-limit bucket every time, making every
 * {@code @RateLimit}-protected endpoint in the app trivially bypassable.
 * This test proves the fix: two requests with different spoofed
 * X-Forwarded-For values but the same real remote address now share the
 * same bucket and the second one gets throttled, exactly as it should.
 */
class RateLimitAspectTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private Map<String, AtomicLong> fakeRedisCounters;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        fakeRedisCounters = new HashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Minimal real INCR semantics backing the mock, since this test's
        // entire point depends on counts actually accumulating per key.
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return fakeRedisCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        });
    }

    @Test
    void spoofedXForwardedForHeader_noLongerBypassesTheRateLimit() throws Throwable {
        RateLimit annotation = rateLimitAnnotation("test-action", 1, 60);

        // Same real client, spoofing a different X-Forwarded-For on every request.
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        firstRequest.setRemoteAddr("203.0.113.50");
        firstRequest.addHeader("X-Forwarded-For", "1.1.1.1");

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setRemoteAddr("203.0.113.50"); // same real peer
        secondRequest.addHeader("X-Forwarded-For", "2.2.2.2"); // different spoofed value

        RateLimitAspect firstAspect = new RateLimitAspect(redisTemplate, firstRequest);
        RateLimitAspect secondAspect = new RateLimitAspect(redisTemplate, secondRequest);

        // First request (limit is 1) succeeds.
        firstAspect.enforceRateLimit(proceedingJoinPoint(), annotation);

        // Second request, from the same real IP but a different spoofed
        // X-Forwarded-For, must now be throttled - if the old XFF-trusting
        // code were still in place, this would incorrectly get its own
        // fresh bucket and succeed.
        assertThatThrownBy(() -> secondAspect.enforceRateLimit(proceedingJoinPoint(), annotation))
                .isInstanceOf(com.saanjha.shared.exception.AppException.class);
    }

    @Test
    void differentRealClients_getIndependentBuckets() throws Throwable {
        RateLimit annotation = rateLimitAnnotation("test-action-2", 1, 60);

        MockHttpServletRequest clientA = new MockHttpServletRequest();
        clientA.setRemoteAddr("203.0.113.10");

        MockHttpServletRequest clientB = new MockHttpServletRequest();
        clientB.setRemoteAddr("203.0.113.20");

        RateLimitAspect aspectA = new RateLimitAspect(redisTemplate, clientA);
        RateLimitAspect aspectB = new RateLimitAspect(redisTemplate, clientB);

        // Both succeed - genuinely different clients must not share a bucket.
        assertThat(aspectA.enforceRateLimit(proceedingJoinPoint(), annotation)).isEqualTo("ok");
        assertThat(aspectB.enforceRateLimit(proceedingJoinPoint(), annotation)).isEqualTo("ok");
    }

    private ProceedingJoinPoint proceedingJoinPoint() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("someMethod");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("ok");
        return joinPoint;
    }

    private RateLimit rateLimitAnnotation(String action, int baseLimit, long baseTimeSeconds) {
        return new RateLimit() {
            @Override
            public String action() {
                return action;
            }

            @Override
            public int baseLimit() {
                return baseLimit;
            }

            @Override
            public long baseTimeSeconds() {
                return baseTimeSeconds;
            }

            @Override
            public String errorMessage() {
                return "Too many requests";
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RateLimit.class;
            }
        };
    }
}
