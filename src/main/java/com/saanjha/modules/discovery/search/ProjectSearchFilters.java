package com.saanjha.modules.discovery.search;

import java.util.List;

/**
 * All filter/keyword inputs for project search. {@code null}/empty means
 * "no constraint" for that dimension. {@code hasOpenSlots} is derived
 * (currentTeamSize &lt; maxTeamSize), not a raw column, so it's expressed as
 * a boolean flag here rather than a numeric range.
 */
public record ProjectSearchFilters(
        String keyword,
        String category,
        List<String> requiredSkills,
        List<String> tags,
        String status,
        Boolean hasOpenSlots
) {
    public static ProjectSearchFilters empty() {
        return new ProjectSearchFilters(null, null, List.of(), List.of(), "RECRUITING", null);
    }
}
