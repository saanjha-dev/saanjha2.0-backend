package com.saanjha.modules.user.event.listener;

import com.saanjha.modules.auth.event.AuthEvents.UserRegisteredEvent;
import com.saanjha.modules.user.entity.UserPreferences;
import com.saanjha.modules.user.entity.UserProfile;
import com.saanjha.modules.user.repository.UserPreferencesRepository;
import com.saanjha.modules.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FIX (TD1/TD21, architecture-review.md §2/§9.1): this was the exact defect
 * the review's concrete failure scenario was written about — {@code @Async}
 * combined with plain {@code @EventListener} means Spring hands the listener
 * to a separate thread the instant {@code publishEvent(...)} is called, with
 * zero synchronization to whether the publishing transaction
 * ({@code AuthService.verifyEmail()}) ever actually commits. If
 * {@code markEmailAsVerified} throws after the event is published, this
 * listener could already have inserted and committed a {@code UserProfile}
 * for a user whose email was never marked verified.
 *
 * {@code team}'s own listeners (`TeamEventListener`, `ProjectTeamEventListener`)
 * already demonstrate the correct pattern in this same repository:
 * {@code @TransactionalEventListener} (default phase AFTER_COMMIT) registers
 * during the publishing transaction's commit and only fires once that commit
 * has actually succeeded — a rollback means this listener never runs at all,
 * closing the orphaned-profile scenario entirely. {@code @Async} is kept
 * alongside it (not dropped) for the same reason as `AuthEventListener`'s
 * fix: AFTER_COMMIT registration is synchronous, dispatch to the async
 * executor happens after, so the auth module's HTTP response is still not
 * delayed by these two inserts — the original comment's stated intent is
 * preserved, just made commit-safe.
 */
@Component
@RequiredArgsConstructor
public class UserModuleEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserModuleEventListener.class);
    private final UserProfileRepository profileRepository;
    private final UserPreferencesRepository preferencesRepository;

    @Async
    @TransactionalEventListener
    @Transactional(propagation  = Propagation.REQUIRES_NEW)
    public void handleUserRegistration(UserRegisteredEvent event) {
        log.info("Provisioning User Profile context for new user: {}", event.userId());

        UserProfile profile = new UserProfile();
        profile.setUserId(event.userId());
        String defaultName = event.email().split("@")[0].replaceAll("[^a-zA-Z0-9]", " ");
        profile.setDisplayName(defaultName);

        profile = profileRepository.save(profile);

        UserPreferences prefs = new UserPreferences();
        prefs.setProfile(profile);
        preferencesRepository.save(prefs);

        log.debug("User Profile and Preferences successfully provisioned for: {}", event.userId());
    }
}
