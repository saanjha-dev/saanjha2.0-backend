package com.saanjha.shared.security;

import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class SecurityUtils {

    // Private constructor to prevent instantiation of utility class
    private SecurityUtils() {
    }

    /**
     * Extracts the authenticated user's UUID from the Spring Security Context.
     * Throws a standard UNAUTHORIZED AppException if no valid context exists.
     */
    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Valid authentication is required to access this resource.");
        }

        return UUID.fromString(authentication.getName());
    }

    /**
     * FIX (S3, architecture-review.md §3 / security-review.md S3):
     * {@code getPublicProfile} is deliberately {@code permitAll()}'d in
     * SecurityConfig — an anonymous recruiter is exactly the persona that
     * route exists for — but it unconditionally called the strict
     * {@code getCurrentUserId()}, so every anonymous call 401'd in practice,
     * silently contradicting the route's own security config.
     *
     * This sibling returns {@code null} instead of throwing for an anonymous
     * or missing security context, so a controller/service can distinguish
     * "no one is logged in" (a valid, expected state for a public endpoint)
     * from "something is actually wrong." It does NOT change behavior for any
     * endpoint that still calls {@code getCurrentUserId()} directly — this is
     * additive, not a replacement, and should only be used by endpoints whose
     * SecurityConfig entry is genuinely {@code permitAll()}.
     */
    public static UUID getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
