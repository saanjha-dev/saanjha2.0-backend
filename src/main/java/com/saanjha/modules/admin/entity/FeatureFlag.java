package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single toggle in the platform's feature-flag system. {@code flagKey} is
 * the stable string every call site checks against (e.g.
 * "discovery.fuzzy-search", "platform.maintenance-mode") — never renamed
 * once shipped, per the same "the key is the contract" principle event names
 * follow elsewhere in this codebase.
 *
 * Evaluation precedence (see FeatureFlagService.isEnabled): kill switch/
 * disabled check first, then explicit user/project allow-list, then
 * percentage rollout, falling back to the plain {@code enabled} boolean for
 * {@link FeatureFlagType#BOOLEAN} flags.
 */
@Entity
@Table(name = "adm_feature_flags", schema = "adm")
@Getter
@Setter
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_key", nullable = false, unique = true, length = 150)
    private String flagKey;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, length = 20)
    private FeatureFlagType flagType = FeatureFlagType.BOOLEAN;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage;

    /** JSON array of allow-listed userIds, only meaningful for {@link FeatureFlagType#USER_LIST}. */

    @Column(name = "target_user_ids", columnDefinition = "TEXT")
    private String targetUserIds;

    /** JSON array of allow-listed projectIds, only meaningful for {@link FeatureFlagType#PROJECT_LIST}. */

    @Column(name = "target_project_ids", columnDefinition = "TEXT")
    private String targetProjectIds;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
