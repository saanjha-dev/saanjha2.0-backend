package com.saanjha.modules.project.service;

import com.saanjha.modules.project.entity.Project;
import com.saanjha.modules.project.entity.ProjectStatus;
import com.saanjha.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Spec H.2 #6 — "Ghosting Leads": if a project sits in RECRUITING for 30 days
 * with no lead activity, auto-archive it so it stops cluttering public search
 * results and wasting applicant attention on a dead listing.
 *
 * SIMPLIFICATION NOTE: the spec frames the trigger as "no lead login", which
 * is Auth-module data. Reaching into Auth's session table would violate the
 * module boundary rule (no cross-schema queries / no cross-module repository
 * access), so this implementation uses the project's own
 * {@code recruitingStartedAt} timestamp as a self-contained proxy instead.
 * A more precise version would have Project consume a future
 * {@code UserAuthenticatedEvent} from Auth to maintain a denormalized
 * "leadLastActiveAt" cache — flagged as a Future Extension Point since that
 * event does not exist in the Auth module yet.
 */
@Component
@RequiredArgsConstructor
public class ProjectGhostingSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ProjectGhostingSchedulerService.class);
    private static final int GHOSTING_THRESHOLD_DAYS = 30;
    private static final String GHOSTING_REASON =
            "Automatically archived: no activity for " + GHOSTING_THRESHOLD_DAYS + " days while RECRUITING.";

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    /** Runs once daily at 03:00 server time — a low-traffic window. */
    @Scheduled(cron = "0 0 3 * * *")
    public void archiveGhostedProjects() {
        Instant cutoff = Instant.now().minus(GHOSTING_THRESHOLD_DAYS, ChronoUnit.DAYS);
        List<Project> ghosted = projectRepository.findByStatusAndRecruitingStartedAtBefore(ProjectStatus.RECRUITING, cutoff);

        if (ghosted.isEmpty()) {
            return;
        }

        log.info("Ghosting sweep: archiving {} project(s) idle in RECRUITING since before {}", ghosted.size(), cutoff);
        for (Project project : ghosted) {
            try {
                projectService.systemArchive(project.getId(), GHOSTING_REASON);
            } catch (Exception ex) {
                // One bad row must never abort the sweep for the rest of the batch.
                log.error("Failed to auto-archive ghosted project {}", project.getId(), ex);
            }
        }
    }
}
