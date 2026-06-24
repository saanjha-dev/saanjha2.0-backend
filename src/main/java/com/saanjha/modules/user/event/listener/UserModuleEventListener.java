package com.saanjha.modules.user.event.listener;

import com.saanjha.modules.auth.event.AuthEvents.UserRegisteredEvent;
import com.saanjha.modules.user.entity.UserPreferences;
import com.saanjha.modules.user.entity.UserProfile;
import com.saanjha.modules.user.repository.UserPreferencesRepository;
import com.saanjha.modules.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserModuleEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserModuleEventListener.class);
    private final UserProfileRepository profileRepository;
    private final UserPreferencesRepository preferencesRepository;

    /**
     * Executes asynchronously so the Auth Module's HTTP response is not delayed
     * by the User Module's database inserts.
     */
    @Async
    @EventListener
    @Transactional
    public void handleUserRegistration(UserRegisteredEvent event) {
        log.info("Provisioning User Profile context for new user: {}", event.userId());

        // 1. Create the default Profile
        UserProfile profile = new UserProfile();
        profile.setUserId(event.userId());
        // Extract the prefix of the email or just generate a fallback name
        String defaultName = event.email().split("@")[0].replaceAll("[^a-zA-Z0-9]", " ");
        profile.setDisplayName(defaultName);
        
        profile = profileRepository.save(profile);

        // 2. Create the default Preferences
        UserPreferences prefs = new UserPreferences();
        prefs.setProfile(profile);
        preferencesRepository.save(prefs);
        
        log.debug("User Profile and Preferences successfully provisioned for: {}", event.userId());
    }
}