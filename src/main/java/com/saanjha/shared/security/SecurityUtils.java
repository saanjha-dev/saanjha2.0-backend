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
}