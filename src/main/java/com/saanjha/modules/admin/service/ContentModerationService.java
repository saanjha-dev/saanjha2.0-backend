package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.*;
import com.saanjha.modules.admin.event.AdminEvents.*;
import com.saanjha.modules.admin.repository.AppealRepository;
import com.saanjha.modules.admin.repository.ModerationActionRepository;
import com.saanjha.modules.admin.repository.ReportRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Content Moderation: the Report Content/User/Project/Message/Portfolio ->
 * Review Queue -> Decision Queue -> Appeal flow (Admin brief, CONTENT
 * MODERATION + TRUST & SAFETY sections). Reports are the intake mechanism;
 * resolving one does not itself take an enforcement action (that's
 * {@code UserModerationService}/{@code ProjectModerationService}/etc.) — a
 * moderator reviews a Report, and separately decides whether to also file a
 * {@link ModerationAction} against the target. This deliberate non-coupling
 * means a report can be legitimately dismissed without that ever implying
 * "no action was possible", and a moderator can act on a target without
 * requiring a report to have been filed first (e.g. proactive enforcement
 * from a TrustScore signal).
 */
@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private final ReportRepository reportRepository;
    private final AppealRepository appealRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final AdminAuditService auditService;
    private final TrustScoreService trustScoreService;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    @Transactional
    public Report submitReport(UUID reporterUserId, ModerationTargetType targetType, UUID targetId, ReportCategory category, String description) {
        Report report = new Report();
        report.setReporterUserId(reporterUserId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setCategory(category);
        report.setDescription(description);
        report = reportRepository.save(report);

        trustScoreService.recordReportFiledAgainst(targetType, targetId);
        eventPublisher.publishEvent(new ReportSubmittedEvent(report.getId(), reporterUserId, targetType.name(), targetId, category.name(), Instant.now()));
        return report;
    }

    @Transactional(readOnly = true)
    public Page<Report> getReviewQueue(List<ReportStatus> statuses, Pageable pageable) {
        return reportRepository.findByStatusInOrderByCreatedAtAsc(statuses, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Report> getModeratorQueue(UUID moderatorId, Pageable pageable) {
        return reportRepository.findByAssignedModeratorIdAndStatusInOrderByCreatedAtAsc(
                moderatorId, List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW, ReportStatus.ESCALATED), pageable);
    }

    @Transactional
    public Report assignReport(UUID actorId, UUID reportId, UUID moderatorId) {
        Report report = getReportOrThrow(reportId);
        report.setAssignedModeratorId(moderatorId);
        if (report.getStatus() == ReportStatus.OPEN) {
            report.setStatus(ReportStatus.IN_REVIEW);
        }
        report = reportRepository.save(report);
        auditService.record(actorId, "REPORT_ASSIGNED", ModerationTargetType.USER, moderatorId, null, reportId.toString(), null);
        return report;
    }

    /** {@code resolution} must be {@link ReportStatus#RESOLVED} or {@link ReportStatus#DISMISSED}. */
    @Transactional
    public Report resolveReport(UUID actorId, UUID reportId, ReportStatus resolution, String notes) {
        if (resolution != ReportStatus.RESOLVED && resolution != ReportStatus.DISMISSED) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Resolution must be RESOLVED or DISMISSED.");
        }
        Report report = getReportOrThrow(reportId);
        report.setStatus(resolution);
        report.setResolutionNotes(notes);
        report.setResolvedBy(actorId);
        report.setResolvedAt(Instant.now());
        report = reportRepository.save(report);

        ModerationAction action = new ModerationAction();
        action.setTargetType(report.getTargetType());
        action.setTargetId(report.getTargetId());
        action.setActionType(resolution == ReportStatus.RESOLVED ? ModerationActionType.REPORT_UPHELD : ModerationActionType.REPORT_DISMISSED);
        action.setActorId(actorId);
        action.setReason(notes);
        action.setRelatedReportId(reportId);
        moderationActionRepository.save(action);

        auditService.record(actorId, "REPORT_RESOLVED", report.getTargetType(), report.getTargetId(), report.getStatus().name(), resolution.name(), notes);

        if (resolution == ReportStatus.RESOLVED) {
            trustScoreService.recordReportUpheld(report.getTargetType(), report.getTargetId());
        }

        eventPublisher.publishEvent(new ReportResolvedEvent(reportId, actorId, resolution.name(), Instant.now()));
        return report;
    }

    private Report getReportOrThrow(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Report not found."));
    }

    // ------------------------------------------------------------------
    // Appeals
    // ------------------------------------------------------------------

    @Transactional
    public Appeal submitAppeal(UUID appellantUserId, UUID moderationActionId, String statement) {
        ModerationAction action = moderationActionRepository.findById(moderationActionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Moderation action not found."));
        if (!action.getTargetId().equals(appellantUserId) && action.getTargetType() == ModerationTargetType.USER) {
            throw new AppException(ErrorCode.FORBIDDEN, "You may only appeal moderation actions taken against you.");
        }
        appealRepository.findByModerationActionIdAndStatusIn(moderationActionId, List.of(AppealStatus.PENDING, AppealStatus.UNDER_REVIEW))
                .ifPresent(existing -> { throw new AppException(ErrorCode.CONFLICT, "An appeal for this action is already pending."); });

        Appeal appeal = new Appeal();
        appeal.setModerationActionId(moderationActionId);
        appeal.setAppellantUserId(appellantUserId);
        appeal.setStatement(statement);
        return appealRepository.save(appeal);
    }

    @Transactional(readOnly = true)
    public Page<Appeal> getAppealQueue(Pageable pageable) {
        return appealRepository.findByStatusInOrderByCreatedAtAsc(List.of(AppealStatus.PENDING, AppealStatus.UNDER_REVIEW), pageable);
    }

    @Transactional
    public Appeal decideAppeal(UUID actorId, UUID appealId, boolean grant, String decisionNotes) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Appeal not found."));
        if (appeal.getStatus() != AppealStatus.PENDING && appeal.getStatus() != AppealStatus.UNDER_REVIEW) {
            throw new AppException(ErrorCode.STATE_TRANSITION_FAILED, "This appeal has already been decided.");
        }

        appeal.setStatus(grant ? AppealStatus.GRANTED : AppealStatus.DENIED);
        appeal.setDecidedBy(actorId);
        appeal.setDecisionNotes(decisionNotes);
        appeal.setDecidedAt(Instant.now());
        appeal = appealRepository.save(appeal);

        if (grant) {
            moderationActionRepository.findById(appeal.getModerationActionId()).ifPresent(action -> {
                action.setReversed(true);
                moderationActionRepository.save(action);
            });
        }

        auditService.record(actorId, "APPEAL_DECIDED", ModerationTargetType.USER, appeal.getAppellantUserId(), null, appeal.getStatus().name(), decisionNotes);
        eventPublisher.publishEvent(new AppealDecidedEvent(appealId, appeal.getModerationActionId(), appeal.getAppellantUserId(), grant, Instant.now()));
        return appeal;
    }
}
