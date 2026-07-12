package com.saanjha.modules.discovery.matching;

import java.util.List;
import java.util.UUID;

/**
 * Project -> Required Skills -> Developer Portfolio -> Contribution ->
 * Availability -> Matching Score, per the brief. Availability is a
 * documented no-op today (see {@code DeveloperSearchDocument}'s Javadoc on
 * {@code availabilityStatus}) -- the interface leaves room for it, but no
 * current factor reads it. Swappable for a future AI-scored implementation
 * without touching {@code DiscoveryController}.
 */
public interface MatchingEngine {
    List<MatchingCandidate> matchDevelopersToProject(UUID projectId, int limit);
}
