package com.saanjha.modules.user.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserEvents {

    /**
     * Triggered whenever a user changes their bio, location, or adds a skill/interest.
     */
    public record ProfileUpdatedEvent(UUID userId) {}

    /**
     * Triggered the exact moment a user's profile score crosses the 80% threshold.
     * The Notification Module listens to this to send a "Congratulations" email,
     * and the Project Module unlocks the ability for them to apply to Premium Projects.
     */
    public record ProfileCompletedEvent(UUID userId) {}

    /**
     * Read-model synchronization event for Discovery, following the same
     * pattern established for Project ({@code ProjectDiscoveryUpdatedEvent}):
     * {@link ProfileUpdatedEvent} carries only a bare {@code userId} (a
     * "something changed, go re-read the aggregate" ping) — workable when
     * Discovery didn't exist, but Discovery now can't query this module's
     * tables directly per the module boundary rule. Rather than enriching
     * the existing ping event, this dedicated event carries exactly the
     * fields Discovery indexes/filters/ranks on. Fired from the same
     * pipeline as {@link ProfileUpdatedEvent}, immediately after it, so the
     * two stay in lockstep by construction.
     */
    public record UserDiscoveryUpdatedEvent(
            UUID userId,
            String displayName,
            String uniqueHandle,
            String headline,
            String bioExcerpt,
            String location,
            String experienceLevel,
            List<SkillSignal> skills,
            List<String> interests,
            int profileScore,
            int projectsCompleted,
            boolean isDeleted,
            Instant occurredAt
    ) {}

    /** Flat value record — no entity references, consistent with every other event payload in the system. */
    public record SkillSignal(String skillName, String skillLevel, boolean isVerified) {}
}