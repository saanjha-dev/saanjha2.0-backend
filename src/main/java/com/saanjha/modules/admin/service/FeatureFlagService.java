package com.saanjha.modules.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.admin.entity.FeatureFlag;
import com.saanjha.modules.admin.entity.FeatureFlagType;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.event.AdminEvents.FeatureFlagChangedEvent;
import com.saanjha.modules.admin.repository.FeatureFlagRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Feature Flags: Global/Module toggles, Percentage Rollout, User/Project
 * allow-lists, and a distinguished Emergency Kill Switch type (Admin brief,
 * FEATURE FLAGS section). Every module in this codebase — including future
 * ones — can check a flag via {@link #isEnabled}, which is the single
 * evaluation entry point so precedence rules never drift between call
 * sites: kill-switch/disabled short-circuit first, then explicit allow-list,
 * then percentage rollout, falling back to the plain boolean.
 *
 * Percentage rollout is deterministic per-user (hash of flagKey+userId modulo
 * 100), not random-per-request — the same user gets a stable answer across
 * repeated calls, which is what "rollout" implies (a user should not flicker
 * in and out of a feature).
 */
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final AdminAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public FeatureFlag createFlag(UUID actorId, String flagKey, String description, FeatureFlagType type,
                                   boolean enabled, Integer rolloutPercentage, List<UUID> targetUserIds, List<UUID> targetProjectIds) {
        if (featureFlagRepository.existsByFlagKey(flagKey)) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "A feature flag with this key already exists.");
        }
        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey(flagKey);
        flag.setDescription(description);
        flag.setFlagType(type);
        flag.setEnabled(enabled);
        flag.setRolloutPercentage(rolloutPercentage);
        flag.setTargetUserIds(toJson(targetUserIds));
        flag.setTargetProjectIds(toJson(targetProjectIds));
        flag.setUpdatedBy(actorId);
        flag = featureFlagRepository.save(flag);

        auditService.record(actorId, "FEATURE_FLAG_CREATED", null, null, null, flagKey, null);
        eventPublisher.publishEvent(new FeatureFlagChangedEvent(flagKey, enabled, actorId, Instant.now()));
        return flag;
    }

    @Transactional
    public FeatureFlag updateFlag(UUID actorId, String flagKey, Boolean enabled, Integer rolloutPercentage,
                                   List<UUID> targetUserIds, List<UUID> targetProjectIds) {
        FeatureFlag flag = getOrThrow(flagKey);
        String before = String.valueOf(flag.isEnabled());
        if (enabled != null) {
            flag.setEnabled(enabled);
        }
        if (rolloutPercentage != null) {
            flag.setRolloutPercentage(rolloutPercentage);
        }
        if (targetUserIds != null) {
            flag.setTargetUserIds(toJson(targetUserIds));
        }
        if (targetProjectIds != null) {
            flag.setTargetProjectIds(toJson(targetProjectIds));
        }
        flag.setUpdatedBy(actorId);
        flag = featureFlagRepository.save(flag);

        auditService.record(actorId, "FEATURE_FLAG_CHANGED", null, null, before, String.valueOf(flag.isEnabled()), null);
        eventPublisher.publishEvent(new FeatureFlagChangedEvent(flagKey, flag.isEnabled(), actorId, Instant.now()));
        return flag;
    }

    /** Emergency Kill Switch: forces {@code enabled=false} immediately, bypassing normal update friction. */
    @Transactional
    public FeatureFlag killSwitch(UUID actorId, String flagKey, String reason) {
        FeatureFlag flag = getOrThrow(flagKey);
        flag.setEnabled(false);
        flag.setUpdatedBy(actorId);
        flag = featureFlagRepository.save(flag);

        auditService.record(actorId, "FEATURE_FLAG_KILL_SWITCH", null, null, "true", "false", reason);
        eventPublisher.publishEvent(new FeatureFlagChangedEvent(flagKey, false, actorId, Instant.now()));
        return flag;
    }

    @Transactional(readOnly = true)
    public List<FeatureFlag> listAll() {
        return featureFlagRepository.findAllByOrderByFlagKeyAsc();
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String flagKey, UUID userId, UUID projectId) {
        return featureFlagRepository.findByFlagKey(flagKey)
                .map(flag -> evaluate(flag, userId, projectId))
                .orElse(false);
    }

    private boolean evaluate(FeatureFlag flag, UUID userId, UUID projectId) {
        if (!flag.isEnabled()) {
            return false;
        }
        return switch (flag.getFlagType()) {
            case BOOLEAN, KILL_SWITCH -> true;
            case USER_LIST -> userId != null && fromJson(flag.getTargetUserIds()).contains(userId);
            case PROJECT_LIST -> projectId != null && fromJson(flag.getTargetProjectIds()).contains(projectId);
            case PERCENTAGE -> userId != null && bucketFor(flag.getFlagKey(), userId) < percentageOrZero(flag);
        };
    }

    private int bucketFor(String flagKey, UUID userId) {
        int hash = (flagKey + ":" + userId).hashCode();
        return Math.abs(hash) % 100;
    }

    private int percentageOrZero(FeatureFlag flag) {
        return flag.getRolloutPercentage() == null ? 0 : flag.getRolloutPercentage();
    }

    private FeatureFlag getOrThrow(String flagKey) {
        return featureFlagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Feature flag not found: " + flagKey));
    }

    private String toJson(List<UUID> ids) {
        try {
            return objectMapper.writeValueAsString(ids == null ? List.of() : ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<UUID> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
