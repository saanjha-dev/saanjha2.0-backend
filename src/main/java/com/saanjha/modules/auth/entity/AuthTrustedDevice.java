package com.saanjha.modules.auth.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_trusted_devices", schema = "auth", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "device_id"})
})
@Getter @Setter
public class AuthTrustedDevice extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser user;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

}
