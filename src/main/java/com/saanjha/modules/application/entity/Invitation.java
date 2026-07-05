package com.saanjha.modules.application.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A second recruitment entry point, owned by the Application module
 * alongside {@link ProjectApplication}. Deliberately has no foreign key or
 * relationship to ProjectApplication — the two are siblings, not parent/child;
 * an accepted invitation is communicated purely via
 * {@code InvitationAcceptedEvent}, never by materializing an Application row.
 */
@Entity
@Table(name = "app_invitations", schema = "app")
@Getter
@Setter
public class Invitation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "invited_user_id", nullable = false)
    private UUID invitedUserId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "preferred_role", length = 100)
    private String preferredRole;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.SENT;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isOpen() {
        return status == InvitationStatus.SENT;
    }
}
