package com.saanjha.modules.team.entity;

/**
 * A team-contextual role — NOT to be confused with the system-wide RBAC roles
 * (ROLE_USER/ROLE_ADMIN) owned by Auth. The same person is LEAD on one team
 * and MEMBER on another simultaneously; this is per-membership-row data.
 */
public enum MembershipRole {
    LEAD,
    MEMBER
}
