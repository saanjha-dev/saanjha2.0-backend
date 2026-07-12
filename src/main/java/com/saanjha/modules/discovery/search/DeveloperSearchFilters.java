package com.saanjha.modules.discovery.search;

import java.util.List;

/**
 * All filter/keyword inputs for developer search.
 * {@code availabilityStatus}/{@code remotePreference} are wired through as
 * optional filters for forward-compatibility with the extension-point
 * columns on {@code DeveloperSearchDocument} — since no event populates
 * those columns today, applying either filter currently yields an empty
 * result set rather than silently ignoring the filter. Callers should not
 * expose these in the UI until an upstream event exists.
 */
public record DeveloperSearchFilters(
        String keyword,
        List<String> skills,
        String experienceLevel,
        Boolean verifiedSkillsOnly,
        Integer minProfileScore,
        String availabilityStatus,
        String remotePreference
) {
    public static DeveloperSearchFilters empty() {
        return new DeveloperSearchFilters(null, List.of(), null, null, null, null, null);
    }
}
