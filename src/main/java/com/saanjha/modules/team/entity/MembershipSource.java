package com.saanjha.modules.team.entity;

/**
 * How a membership row came to exist. Captured once at creation and never
 * reconstructed after the fact — per the architecture spec's "never rely on
 * reconstructing history later" principle.
 */
public enum MembershipSource {
    APPLICATION,
    INVITATION,
    MANUAL,
    MIGRATION,
    REJOINED
}
