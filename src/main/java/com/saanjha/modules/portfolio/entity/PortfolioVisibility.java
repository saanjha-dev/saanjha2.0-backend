package com.saanjha.modules.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per user. Defaults to PUBLIC — the module's stated purpose is to
 * be evidence recruiters and collaborators consult, so opt-out (not
 * opt-in) matches that intent, same default philosophy Contribution applied
 * to its own Summary/Reputation reads. A user who never visits this setting
 * still gets a discoverable, credible portfolio.
 *
 * {@code shareToken} is only ever non-null while visibility is LINK_ONLY —
 * generated fresh each time the user (re)shares, high-entropy, never
 * sequential (anti-enumeration).
 */
@Entity
@Table(name = "ptf_visibility", schema = "ptf")
@Getter
@Setter
public class PortfolioVisibility {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private PortfolioVisibilityType visibility = PortfolioVisibilityType.PUBLIC;

    @Column(name = "share_token", unique = true, length = 64)
    private String shareToken;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static PortfolioVisibility defaultFor(UUID userId) {
        PortfolioVisibility visibility = new PortfolioVisibility();
        visibility.userId = userId;
        return visibility;
    }
}
