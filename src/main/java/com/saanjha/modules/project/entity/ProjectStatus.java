package com.saanjha.modules.project.entity;

/**
 * The Project lifecycle state machine (Spec Section F.1).
 *
 * DRAFT --publish--> RECRUITING --lock team--> IN_PROGRESS --finalize--> COMPLETED
 * DRAFT, RECRUITING, IN_PROGRESS --abandon--> ARCHIVED
 *
 * COMPLETED and ARCHIVED are terminal: no outbound transitions exist.
 * Transition legality is centrally enforced by {@link com.saanjha.modules.project.service.ProjectStatusTransitionValidator},
 * never scattered across services or controllers.
 */
public enum ProjectStatus {
    DRAFT,
    RECRUITING,
    IN_PROGRESS,
    COMPLETED,
    ARCHIVED
}
