package com.saanjha.modules.portfolio.entity;

/**
 * {@code RECRUITER_ONLY} is deliberately NOT included yet — it would need a
 * "recruiter" identity concept that doesn't exist anywhere in this codebase
 * (auth only has ROLE_USER/ROLE_ADMIN). Documented as a future extension
 * point rather than added as a dead enum value nothing can ever set.
 */
public enum PortfolioVisibilityType {
    PUBLIC,
    PRIVATE,
    LINK_ONLY
}
