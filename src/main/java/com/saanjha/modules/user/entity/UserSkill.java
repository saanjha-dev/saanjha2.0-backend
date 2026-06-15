package com.saanjha.modules.user.entity;

import com.saanjha.shared.audit.BaseAuditEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usr_skills", schema = "usr", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"profile_id", "skill_name"})
})
@org.hibernate.annotations.SQLRestriction("is_deleted = false") // ADD THIS LINE
@Getter @Setter
public class UserSkill extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private UserProfile profile;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "skill_level", nullable = false, length = 20)
    private String skillLevel; // BEGINNER, INTERMEDIATE, ADVANCED

    @Column(nullable = false)
    private boolean isVerified = false;

    private UUID verifiedBy; // The user ID of the team lead/admin who verified this
    
    private Instant verifiedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
}