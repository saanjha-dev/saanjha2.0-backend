package com.saanjha.modules.auth.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions", schema = "auth")
@Getter @Setter
public class AuthSession extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private String deviceId;

    @Column(name = "device_ip", nullable = false)
    private String deviceIp;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;
}