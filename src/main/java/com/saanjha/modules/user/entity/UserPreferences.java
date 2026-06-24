package com.saanjha.modules.user.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "usr_preferences", schema = "usr")
@org.hibernate.annotations.SQLRestriction("is_deleted = false") // ADD THIS LINE
@Getter @Setter
public class UserPreferences extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private UserProfile profile;

    @Column(length = 20)
    private String theme = "DARK";

    @Column(nullable = false)
    private boolean emailNotifications = true;

    @Column(length = 20)
    private String profileVisibility = "PUBLIC"; // PUBLIC, PRIVATE, CONNECTIONS_ONLY

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
}