package com.saanjha.modules.user.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "usr_social_links", schema = "usr", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"profile_id", "platform_name"})
})
@org.hibernate.annotations.SQLRestriction("is_deleted = false") // ADD THIS LINE
@Getter @Setter
public class UserSocialLink extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private UserProfile profile;

    @Column(name = "platform_name", nullable = false, length = 50)
    private String platformName; // GITHUB, LINKEDIN, PORTFOLIO, X

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
}