package com.saanjha.modules.user.event;

import java.util.UUID;

public class UserEvents {

    /**
     * Triggered whenever a user changes their bio, location, or adds a skill/interest.
     * The Discovery Module listens to this to update the ElasticSearch/Search Index.
     */
    public record ProfileUpdatedEvent(UUID userId) {}

    /**
     * Triggered the exact moment a user's profile score crosses the 80% threshold.
     * The Notification Module listens to this to send a "Congratulations" email,
     * and the Project Module unlocks the ability for them to apply to Premium Projects.
     */
    public record ProfileCompletedEvent(UUID userId) {}
}