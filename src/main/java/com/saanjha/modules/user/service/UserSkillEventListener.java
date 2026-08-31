package com.saanjha.modules.user.service;

import com.saanjha.modules.portfolio.event.PortfolioEvents.SkillsVerifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserSkillEventListener {

    private final UserProfileService userProfileService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSkillsVerified(SkillsVerifiedEvent event) {
        userProfileService.verifyProjectSkills(
                event.userId(),
                event.skills(),
                event.skillLevel(),
                event.verifierId(),
                event.verifiedAt()
        );
    }
}
