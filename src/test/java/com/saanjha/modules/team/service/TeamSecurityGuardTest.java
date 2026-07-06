package com.saanjha.modules.team.service;

import com.saanjha.modules.team.entity.MembershipStatus;
import com.saanjha.modules.team.repository.MembershipRepository;
import com.saanjha.modules.team.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for TD18/S12 (architecture-review.md §9.3): proves the
 * fixed guard actually distinguishes "any authenticated user" from "a member
 * of this team", and that the PUBLIC-visibility exception only applies where
 * it's supposed to.
 */
@ExtendWith(MockitoExtension.class)
class TeamSecurityGuardTest {

    @Mock private TeamRepository teamRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private TeamService teamService;

    private TeamSecurityGuard guard;

    private UUID teamId;
    private UUID memberUserId;
    private UUID strangerUserId;

    @BeforeEach
    void setUp() {
        guard = new TeamSecurityGuard(teamRepository, membershipRepository, teamService);
        teamId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();
        strangerUserId = UUID.randomUUID();
    }

    @Test
    void isMember_returnsFalse_forAStrangerOnAPrivateTeam() {
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(strangerUserId), any()))
                .thenReturn(Optional.empty());

        assertThat(guard.isMember(teamId, strangerUserId.toString())).isFalse();
    }

    @Test
    void isVisibleTo_returnsFalse_forAStranger_whenTeamIsNotPublic() {
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(strangerUserId), any()))
                .thenReturn(Optional.empty());
        when(teamService.isPubliclyVisible(teamId)).thenReturn(false);

        assertThat(guard.isVisibleTo(teamId, strangerUserId.toString())).isFalse();
    }

    @Test
    void isVisibleTo_returnsTrue_forAStranger_whenTeamIsPublic() {
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(strangerUserId), any()))
                .thenReturn(Optional.empty());
        when(teamService.isPubliclyVisible(teamId)).thenReturn(true);

        assertThat(guard.isVisibleTo(teamId, strangerUserId.toString())).isTrue();
    }

    @Test
    void isVisibleTo_returnsTrue_forALiveMember_regardlessOfVisibilitySetting() {
        com.saanjha.modules.team.entity.Membership membership = new com.saanjha.modules.team.entity.Membership();
        membership.setUserId(memberUserId);
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(memberUserId), any()))
                .thenReturn(Optional.of(membership));

        assertThat(guard.isVisibleTo(teamId, memberUserId.toString())).isTrue();
        // A live member never needs the visibility fallback to be checked at all.
        verify(teamService, never()).isPubliclyVisible(any());
    }

    @Test
    void isMember_isNeverSatisfiedByPublicVisibilityAlone() {
        // Sensitive endpoints (history, metrics) use isMember directly, not isVisibleTo —
        // this proves that path is never accidentally satisfied by a PUBLIC setting.
        when(membershipRepository.findByTeam_IdAndUserIdAndStatusIn(eq(teamId), eq(strangerUserId), any()))
                .thenReturn(Optional.empty());

        assertThat(guard.isMember(teamId, strangerUserId.toString())).isFalse();
        verifyNoInteractions(teamService);
    }
}
