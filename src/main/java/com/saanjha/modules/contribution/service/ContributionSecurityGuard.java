package com.saanjha.modules.contribution.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Privacy model for Contribution reads, deliberately simpler than Team's:
 *
 *  - {@code SummaryResponse}/{@code ReputationResponse} (the polished,
 *    portfolio-facing view) are visible to ANY authenticated platform user —
 *    the module's entire stated purpose is to be evidence recruiters,
 *    hiring managers, and collaborators consult, so treating this as
 *    broadly visible by default matches that intent rather than fighting it.
 *  - The raw ledger TIMELINE (full detail: every entry, every integrity
 *    flag, every correction) is more sensitive and stays owner-or-moderator
 *    only — see {@code isOwner}, composed with {@code hasAuthority('contribution:moderate')}
 *    at the controller layer.
 *
 * This does NOT yet integrate with User's own profile-visibility
 * preferences (Public/Private/Connections-Only) — doing so would require
 * either a cross-module synchronous call User doesn't currently expose for
 * this purpose, or a synced cache built from a new User event. Documented
 * as a known follow-up, not silently ignored — see the module write-up's
 * Extension Points.
 */
@Component("contributionGuard")
public class ContributionSecurityGuard {

    public boolean isOwner(UUID profileUserId, String userIdText) {
        if (profileUserId == null || userIdText == null) {
            return false;
        }
        return profileUserId.toString().equalsIgnoreCase(userIdText);
    }
}
