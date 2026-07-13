package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.entity.TrustRiskLevel;
import com.saanjha.modules.admin.entity.TrustScore;
import com.saanjha.modules.admin.event.AdminEvents.TrustScoreDegradedEvent;
import com.saanjha.modules.admin.repository.TrustScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * A deliberately simple, fully-explainable weighted-counter risk score —
 * not a model. This is the schema and recalculation seam the Admin brief's
 * "Future AI hooks" note asks for: {@link #recalculate} is the single method
 * a future ML-based scorer would replace, without any caller (Reports,
 * AdminEventListener, dashboards) needing to change, since they only ever
 * read {@code TrustScore.score}/{@code riskLevel}.
 *
 * Every user starts at a neutral baseline (100, LOW risk) and is never
 * created reactively from a report alone — {@link #getOrCreate} exists so a
 * first-time report doesn't itself need special-case handling.
 */
@Service
@RequiredArgsConstructor
public class TrustScoreService {

    private final TrustScoreRepository trustScoreRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void recordReportFiledAgainst(ModerationTargetType targetType, UUID targetId) {
        if (targetType != ModerationTargetType.USER) {
            return; // Trust scoring is a per-user concept today; project/team-level trust is a future extension.
        }
        TrustScore trustScore = getOrCreate(targetId);
        trustScore.setReportCount(trustScore.getReportCount() + 1);
        recalculate(trustScore);
    }

    @Transactional
    public void recordReportUpheld(ModerationTargetType targetType, UUID targetId) {
        if (targetType != ModerationTargetType.USER) {
            return;
        }
        TrustScore trustScore = getOrCreate(targetId);
        trustScore.setUpheldReportCount(trustScore.getUpheldReportCount() + 1);
        recalculate(trustScore);
    }

    @Transactional
    public void recordSuspiciousActivity(UUID userId) {
        TrustScore trustScore = getOrCreate(userId);
        trustScore.setSuspiciousActivityCount(trustScore.getSuspiciousActivityCount() + 1);
        recalculate(trustScore);
    }

    private TrustScore getOrCreate(UUID userId) {
        return trustScoreRepository.findByUserId(userId).orElseGet(() -> {
            TrustScore trustScore = new TrustScore();
            trustScore.setUserId(userId);
            return trustScore;
        });
    }

    /**
     * Weighted-deduction heuristic: an upheld report costs the most (it's a
     * confirmed violation), a raw report costs a little (reports can be
     * frivolous — see the "repeated reports" nuance in the Admin brief,
     * which this deliberately does not over-index on by weighting raw
     * report count low), and a suspicious-activity signal from Auth costs
     * moderately (it's automated, not human-confirmed).
     */
    private void recalculate(TrustScore trustScore) {
        double score = 100.0
                - (trustScore.getReportCount() * 1.5)
                - (trustScore.getUpheldReportCount() * 15.0)
                - (trustScore.getSuspiciousActivityCount() * 8.0);
        score = Math.max(0.0, Math.min(100.0, score));
        trustScore.setScore(score);
        trustScore.setRiskLevel(riskLevelFor(score));
        trustScore.setLastRecalculatedAt(Instant.now());
        trustScoreRepository.save(trustScore);

        if (trustScore.getRiskLevel() == TrustRiskLevel.HIGH || trustScore.getRiskLevel() == TrustRiskLevel.CRITICAL) {
            eventPublisher.publishEvent(new TrustScoreDegradedEvent(trustScore.getUserId(), score, trustScore.getRiskLevel().name(), Instant.now()));
        }
    }

    private TrustRiskLevel riskLevelFor(double score) {
        if (score >= 70) return TrustRiskLevel.LOW;
        if (score >= 40) return TrustRiskLevel.MEDIUM;
        if (score >= 15) return TrustRiskLevel.HIGH;
        return TrustRiskLevel.CRITICAL;
    }
}
