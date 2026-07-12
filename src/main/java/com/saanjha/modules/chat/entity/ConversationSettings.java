package com.saanjha.modules.chat.entity;

/**
 * Chat's own per-conversation configuration, stored as JSONB on
 * {@link Conversation#getSettingsJson()} - same convention as
 * tem.tem_teams.settings / TeamSettings: new settings are addable without a
 * migration, this record is the typed contract on top of the loose storage.
 *
 * {@code slowModeSeconds == 0} means slow mode is off. {@code
 * onlyAdminsCanPost} is meaningful only for PROJECT_ANNOUNCEMENTS-type
 * conversations by convention, but is not restricted to that type at the
 * data level - a GROUP owner may reasonably want the same behavior.
 */
public record ConversationSettings(
        boolean onlyAdminsCanPost,
        boolean allowThreads,
        boolean allowReactions,
        int slowModeSeconds,
        boolean allowExternalReferences
) {
    public static ConversationSettings defaults() {
        return new ConversationSettings(false, true, true, 0, true);
    }

    public static ConversationSettings announcementsDefaults() {
        return new ConversationSettings(true, true, true, 0, true);
    }
}
