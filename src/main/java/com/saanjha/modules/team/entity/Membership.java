package com.saanjha.modules.team.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One roster seat. A user may hold multiple Membership rows for the same
 * team over time (an original stint that ended in LEFT, followed by a
 * REJOINED stint) — rows are never resurrected, only ever created fresh and
 * then moved forward to a terminal status. See {@link MembershipStatus}'s
 * Javadoc for the full reasoning.
 *
 * {@code sourceReferenceId} carries the originating Application or
 * Invitation id (null for MANUAL/MIGRATION rows) and is also this row's
 * idempotency key: a duplicate delivery of the event that created it must
 * find the existing row via this field rather than create a second one —
 * enforced at the DB level by a partial unique index, not just checked in code.
 */
@Entity
@Table(name = "tem_memberships", schema = "tem")
@Getter
@Setter
public class Membership extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role = MembershipRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "joined_via", nullable = false, length = 20)
    private MembershipSource joinedVia;

    @Column(name = "source_reference_id")
    private UUID sourceReferenceId;

    @Column(name = "contribution_title", length = 100)
    private String contributionTitle;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isLive() {
        return status == MembershipStatus.ACTIVE || status == MembershipStatus.SUSPENDED;
    }

    public boolean isLead() {
        return role == MembershipRole.LEAD && status == MembershipStatus.ACTIVE;
    }
}
