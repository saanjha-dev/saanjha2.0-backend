package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.PlatformSetting;
import com.saanjha.modules.admin.entity.PlatformSettingValueType;
import com.saanjha.modules.admin.event.AdminEvents.PlatformConfigurationChangedEvent;
import com.saanjha.modules.admin.event.AdminEvents.SystemMaintenanceEndedEvent;
import com.saanjha.modules.admin.event.AdminEvents.SystemMaintenanceStartedEvent;
import com.saanjha.modules.admin.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform Configuration: System Settings, Limits, Thresholds, Retention
 * Policies, Registration Controls, and Read-only/Maintenance Mode (Admin
 * brief, PLATFORM CONFIGURATION section). A flat key/value/type table (see
 * {@link PlatformSetting}'s javadoc) rather than typed columns per setting.
 *
 * {@code platform.maintenance_mode} and {@code platform.registration_open}
 * are just settings under this same mechanism — no special-cased entity —
 * but {@link #enterMaintenanceMode}/{@link #exitMaintenanceMode} exist as
 * named convenience methods because they are the two operationally critical
 * settings this module's SecurityConfig/registration flow would need to
 * check (see Future Extension Points: wiring that check into
 * {@code SecurityConfig}/{@code AuthService.register} is a small follow-up,
 * not yet done, since it touches a different module's request path).
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    public static final String MAINTENANCE_MODE_KEY = "platform.maintenance_mode";
    public static final String REGISTRATION_OPEN_KEY = "platform.registration_open";

    private final PlatformSettingRepository settingRepository;
    private final AdminAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<PlatformSetting> listAll() {
        return settingRepository.findAllByOrderBySettingKeyAsc();
    }

    @Transactional
    public PlatformSetting upsertSetting(UUID actorId, String key, String value, PlatformSettingValueType type, String description) {
        PlatformSetting setting = settingRepository.findBySettingKey(key).orElseGet(() -> {
            PlatformSetting s = new PlatformSetting();
            s.setSettingKey(key);
            return s;
        });
        String before = setting.getSettingValue();
        setting.setSettingValue(value);
        setting.setValueType(type);
        if (description != null) {
            setting.setDescription(description);
        }
        setting.setUpdatedBy(actorId);
        setting = settingRepository.save(setting);

        auditService.record(actorId, "PLATFORM_SETTING_CHANGED", null, null, before, value, null);
        eventPublisher.publishEvent(new PlatformConfigurationChangedEvent(key, value, actorId, Instant.now()));
        return setting;
    }

    @Transactional
    public void enterMaintenanceMode(UUID actorId, String reason, Instant estimatedEndAt) {
        upsertSetting(actorId, MAINTENANCE_MODE_KEY, "true", PlatformSettingValueType.BOOLEAN, "Global read-only/maintenance mode.");
        eventPublisher.publishEvent(new SystemMaintenanceStartedEvent(actorId, reason, estimatedEndAt, Instant.now()));
    }

    @Transactional
    public void exitMaintenanceMode(UUID actorId) {
        upsertSetting(actorId, MAINTENANCE_MODE_KEY, "false", PlatformSettingValueType.BOOLEAN, "Global read-only/maintenance mode.");
        eventPublisher.publishEvent(new SystemMaintenanceEndedEvent(actorId, Instant.now()));
    }

    @Transactional(readOnly = true)
    public boolean isMaintenanceModeActive() {
        return settingRepository.findBySettingKey(MAINTENANCE_MODE_KEY)
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(false);
    }
}
