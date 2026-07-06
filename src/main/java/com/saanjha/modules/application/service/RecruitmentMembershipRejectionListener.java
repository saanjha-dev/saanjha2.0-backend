package com.saanjha.modules.application.service;

import com.saanjha.modules.team.event.TeamEvents.MembershipCreationRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FIX (TD19, architecture-review.md §9.2 / technical-debt.md TD19): before
 * this listener existed, {@code TeamService.addMember} correctly detected a
 * lost last-slot capacity race and published
 * {@code MembershipCreationRejectedEvent} instead of silently failing — but
 * nothing in `application`/`invitation` was listening for it. The affected
 * {@code ProjectApplication}/{@code Invitation} row stayed permanently
 * ACCEPTED with no seat: a silent, guaranteed data-integrity gap the moment
 * two accepts landed close together, not a hypothetical one.
 *
 * This is a thin adapter, same philosophy as {@code TeamEventListener}: it
 * only decides which service to call based on {@code sourceType}, all actual
 * compensating logic lives in {@code ApplicationService.reopenAfterSeatLost}
 * / {@code InvitationService.markSeatLost}.
 */
@Component
@RequiredArgsConstructor
public class RecruitmentMembershipRejectionListener {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentMembershipRejectionListener.class);

    private final ApplicationService applicationService;
    private final InvitationService invitationService;

    @TransactionalEventListener
    public void onMembershipCreationRejected(MembershipCreationRejectedEvent event) {
        try {
            switch (event.sourceType()) {
                case "APPLICATION" -> applicationService.reopenAfterSeatLost(event.sourceReferenceId(), event.reason());
                case "INVITATION" -> invitationService.markSeatLost(event.sourceReferenceId(), event.reason());
                default -> log.warn("Unknown sourceType '{}' on MembershipCreationRejectedEvent for project {}",
                        event.sourceType(), event.projectId());
            }
        } catch (Exception ex) {
            // A failure compensating for a lost race must not become a second
            // failure on top of the first — log loudly, this is exactly the
            // kind of thing worth alerting on once real observability exists.
            log.error("Failed to compensate for rejected membership creation (sourceType={}, sourceReferenceId={}, project={})",
                    event.sourceType(), event.sourceReferenceId(), event.projectId(), ex);
        }
    }
}
