package com.saanjha.modules.admin.entity;

/**
 * The kind of platform aggregate a moderation action, report, or appeal is
 * about. Deliberately a flat enum rather than a polymorphic entity reference
 * — Admin never holds a JPA relationship into another module's schema (the
 * boundary rule from the approved architecture spec, Section 1: "no direct
 * JPA repository access across module boundaries"). {@code targetId} is a
 * logical (non-FK) UUID resolved against whichever module owns this type at
 * read time, via that module's own service — never a cross-schema join.
 */
public enum ModerationTargetType {
    USER,
    PROJECT,
    TEAM,
    TEAM_MEMBERSHIP,
    CHAT_MESSAGE,
    CHAT_CONVERSATION,
    PORTFOLIO,
    TASK,
    APPLICATION
}
