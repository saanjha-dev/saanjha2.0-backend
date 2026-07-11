package com.saanjha.modules.auth.service;

import java.util.Optional;
import java.util.UUID;

/**
 * A Service Contract (per the platform's boundary rule), following the exact
 * precedent {@code project.service.ProjectSnapshotProvider} set: this
 * module's own javadoc there justifies a synchronous port only when it's
 * invoked at one well-defined moment, not repeatedly per event or per read.
 * That justification applies identically here - Notification's dispatch
 * layer calls this exactly once per EMAIL delivery attempt, immediately
 * before rendering/sending, so the address is always current (never cached
 * or snapshotted at enqueue time, since a user's email can change between a
 * notification being queued and a DIGEST-deferred send actually firing).
 * <p>
 * {@code email} is the only field Auth's {@code AuthUser} needs to expose
 * for this purpose - deliberately not a broader "AuthUserSnapshot" grab-bag.
 * Built for the Notification module because {@code auth} is the sole owner
 * of verified email (the {@code user} module's {@code UserProfile} never
 * persists it - confirmed by reading {@code UserModuleEventListener}, which
 * only derives a display name from {@code UserRegisteredEvent.email()} and
 * discards the address itself).
 */
public interface AuthContactProvider {

    /**
     * @return the user's email, only if it has completed email verification
     * (never returns an unverified address - Notification must not be the
     * first module in this codebase to leak notifications to an address
     * nobody has proven ownership of). Empty if the user doesn't exist or
     * hasn't verified.
     */
    Optional<String> getVerifiedEmail(UUID userId);
}
