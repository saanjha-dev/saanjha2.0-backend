package com.saanjha.modules.auth.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_verification_codes", schema = "auth")
@Getter @Setter
public class VerificationCode extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Purpose purpose;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "is_used", nullable = false)
    private boolean used = false;

    public enum Purpose {
        EMAIL_VERIFICATION, PASSWORD_RESET
    }
}