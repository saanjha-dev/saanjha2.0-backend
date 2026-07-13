package com.saanjha.modules.admin.entity;

/**
 * BOOLEAN        -> a plain on/off switch, gated only by `enabled`.
 * PERCENTAGE     -> deterministic hash-bucket rollout via `rolloutPercentage`.
 * USER_LIST      -> allow-listed to specific userIds regardless of percentage.
 * PROJECT_LIST   -> allow-listed to specific projectIds regardless of percentage.
 * KILL_SWITCH    -> same evaluation as BOOLEAN, but surfaced separately in the
 *                   dashboard and permitted to bypass normal change-review
 *                   friction for emergencies (Section "Emergency Kill Switch").
 */
public enum FeatureFlagType {
    BOOLEAN,
    PERCENTAGE,
    USER_LIST,
    PROJECT_LIST,
    KILL_SWITCH
}
