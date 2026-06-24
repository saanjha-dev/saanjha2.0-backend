package com.saanjha.modules.user.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "usr_interests", schema = "usr", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"profile_id", "interest_name"})
})
@org.hibernate.annotations.SQLRestriction("is_deleted = false") // ADD THIS LINE
@Getter @Setter
public class UserInterest extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private UserProfile profile;

    @Column(name = "interest_name", nullable = false, length = 100)
    private String interestName;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
}