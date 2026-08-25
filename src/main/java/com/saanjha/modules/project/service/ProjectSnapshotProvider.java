package com.saanjha.modules.project.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Service Contract (per the platform's boundary rule: cross-module reads
 * happen only through Events, Service Contracts, or Read Models — never a
 * direct repository reach-through). This is the first synchronous port this
 * codebase has needed; every prior cross-module dependency (Team ->
 * Portfolio, Contribution -> Portfolio) was solved by enriching the
 * outbound event instead. A synchronous call is justified here specifically
 * because it is invoked exactly ONCE, at the single, well-defined moment a
 * Portfolio entry is generated — not on every read of a portfolio, and not
 * repeatedly per event. Enriching {@code ProjectCompletedEvent} instead was
 * considered and rejected: that event is already consumed by Team and
 * Contribution, and widening its payload for a concern only Portfolio has
 * would couple an established contract to a downstream module's snapshot
 * needs.
 *
 * Deliberately returns a flat, immutable {@link ProjectSnapshot} value — no
 * entity reference ever crosses this boundary, matching the same
 * no-entity-leakage constraint the event payloads in this codebase already
 * enforce (see TeamEvents' Javadoc).
 */
public interface ProjectSnapshotProvider {

    /**
     * @return the project's display-facing facts, exactly as they stood at
     * call time. Empty only if the project id is somehow unresolvable — in
     * practice this should not happen for a project that just reached
     * COMPLETED, since Project has no hard-delete path (ARCHIVED is the only
     * terminal/soft-delete state), but callers must not assume it's
     * impossible.
     */
    Optional<ProjectSnapshot> getSnapshot(UUID projectId);

    /**
     * Immutable, self-contained snapshot of a project's display facts at one
     * instant. Once captured by a caller (Portfolio), it is never re-fetched
     * or refreshed — later edits to the live Project row must never alter
     * history that already quotes this snapshot.
     */
    record ProjectSnapshot(
            UUID projectId,
            String title,
            String slug,
            String category,
            String descriptionExcerpt,
            List<String> technologyTags,
            List<RequirementSnapshot> requirements
    ) {}

    record RequirementSnapshot(
            String roleName,
            java.util.Set<String> skills,
            String skillLevel
    ) {}
}
