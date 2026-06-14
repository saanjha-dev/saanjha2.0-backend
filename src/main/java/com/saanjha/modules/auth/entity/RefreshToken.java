package com.saanjha.modules.auth.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_tokens", schema = "auth")
@Getter @Setter
public class RefreshToken extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "parent_token_id")
    private UUID parentTokenId; // Used for Token Family Tree tracking

    @Column(name = "is_used", nullable = false)
    private boolean used = false;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
}