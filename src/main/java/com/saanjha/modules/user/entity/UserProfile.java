package com.saanjha.modules.user.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "usr_profiles", schema = "usr")
@org.hibernate.annotations.SQLRestriction("is_deleted = false") // ADD THIS LINE
@Getter @Setter
public class UserProfile extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId; // Logical link to auth.auth_users

    private String displayName;

    @Column(name = "unique_handle", length = 50)
    private String uniqueHandle;
    private String headline;
    
    @Column(columnDefinition = "TEXT")
    private String bio;
    
    private String location;
    private String college;
    private String experienceLevel;
    
    @Column(length = 500)
    private String profileImageUrl;
    
    private int profileScore = 0;
    private int projectsCompleted = 0;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    // --- RELATIONSHIPS ---

    @OneToOne(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserPreferences preferences;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserSkill> skills = new HashSet<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserInterest> interests = new HashSet<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserSocialLink> socialLinks = new HashSet<>();

    // --- BIDIRECTIONAL HELPER METHODS ---

    public void setPreferences(UserPreferences preferences) {
        this.preferences = preferences;
        if (preferences != null) {
            preferences.setProfile(this);
        }
    }

    public void addSkill(UserSkill skill) {
        skills.add(skill);
        skill.setProfile(this);
    }

    public void removeSkill(UserSkill skill) {
        skills.remove(skill);
        skill.setProfile(null);
    }

    public void addInterest(UserInterest interest) {
        interests.add(interest);
        interest.setProfile(this);
    }

    public void removeInterest(UserInterest interest) {
        interests.remove(interest);
        interest.setProfile(null);
    }

    public void addSocialLink(UserSocialLink link) {
        socialLinks.add(link);
        link.setProfile(this);
    }

    public void removeSocialLink(UserSocialLink link) {
        socialLinks.remove(link);
        link.setProfile(null);
    }
}