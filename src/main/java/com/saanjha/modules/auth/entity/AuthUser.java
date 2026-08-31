package com.saanjha.modules.auth.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "auth_users", schema = "auth")
@Getter @Setter
public class AuthUser extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auth_provider", nullable = false)
    private String authProvider = "LOCAL";

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "is_mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    // REMOVE the old private Role role; field.
    // ADD THIS RELATIONAL MAPPING:
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "auth_user_roles",
            schema = "auth",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private java.util.Set<AuthRole> roles = new java.util.HashSet<>();

    /**
     * FEATURE (Admin module, User Moderation): {@code BANNED} added alongside
     * the pre-existing {@code SUSPENDED}/{@code LOCKED} values so Admin can
     * express a permanent moderation outcome distinctly from a temporary one
     * — both currently gate login identically (see {@code AuthService.login}),
     * but are surfaced differently in Admin's read models. No CHECK constraint
     * exists on the {@code account_status} column (see V1__auth_schema.sql),
     * so this is a Java-only, additive change — no migration required.
     */
    public enum AccountStatus {
        ACTIVE, SUSPENDED, LOCKED, BANNED
    }
}