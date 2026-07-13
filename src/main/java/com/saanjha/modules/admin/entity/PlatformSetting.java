package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single platform-wide configuration value (limits, thresholds, retention
 * policies, registration controls, read-only mode). Stored as a flat
 * key/value/type triple rather than one column per setting — new settings
 * ship without a migration, matching {@code TeamSettings}'s own JSONB-for-
 * extensibility precedent (see V10 migration comment in the Team module).
 */
@Entity
@Table(name = "adm_platform_settings", schema = "adm")
@Getter
@Setter
public class PlatformSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 150)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private PlatformSettingValueType valueType = PlatformSettingValueType.STRING;

    @Column(length = 500)
    private String description;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
