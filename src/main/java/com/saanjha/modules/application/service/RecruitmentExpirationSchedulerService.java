package com.saanjha.modules.application.service;

import com.saanjha.modules.application.entity.ApplicationStatus;
import com.saanjha.modules.application.entity.InvitationStatus;
import com.saanjha.modules.application.entity.ProjectApplication;
import com.saanjha.modules.application.entity.Invitation;
import com.saanjha.modules.application.repository.InvitationRepository;
import com.saanjha.modules.application.repository.ProjectApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Auto-expires overdue Applications and Invitations (Spec: "Auto expiration",
 * "Expired invitations"). Combined into one scheduler class, rather than two
 * near-identical ones, since both sweeps share the exact same shape — find
 * overdue open rows, transition each through its owning service, never let
 * one bad row abort the batch — and a single daily job is simpler to
 * monitor/alert on than two.
 */
@Component
@RequiredArgsConstructor
public class RecruitmentExpirationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentExpirationSchedulerService.class);

    private final ProjectApplicationRepository applicationRepository;
    private final InvitationRepository invitationRepository;
    private final ApplicationService applicationService;
    private final InvitationService invitationService;

    private static final List<ApplicationStatus> OPEN_APPLICATION_STATUSES =
            List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED);

    /** Runs hourly — recruitment deadlines are time-sensitive enough that a once-daily sweep would feel sluggish. */
    @Scheduled(cron = "0 0 * * * *")
    public void expireOverdueApplications() {
        Instant now = Instant.now();
        List<ProjectApplication> overdue = applicationRepository.findByStatusInAndExpiresAtBefore(OPEN_APPLICATION_STATUSES, now);
        if (overdue.isEmpty()) {
            return;
        }
        log.info("Expiration sweep: expiring {} overdue application(s)", overdue.size());
        for (ProjectApplication application : overdue) {
            try {
                applicationService.systemExpire(application.getId());
            } catch (Exception ex) {
                log.error("Failed to auto-expire application {}", application.getId(), ex);
            }
        }
    }

    @Scheduled(cron = "0 30 * * * *")
    public void expireOverdueInvitations() {
        Instant now = Instant.now();
        List<Invitation> overdue = invitationRepository.findByStatusAndExpiresAtBefore(InvitationStatus.SENT, now);
        if (overdue.isEmpty()) {
            return;
        }
        log.info("Expiration sweep: expiring {} overdue invitation(s)", overdue.size());
        for (Invitation invitation : overdue) {
            try {
                invitationService.systemExpire(invitation.getId());
            } catch (Exception ex) {
                log.error("Failed to auto-expire invitation {}", invitation.getId(), ex);
            }
        }
    }
}
