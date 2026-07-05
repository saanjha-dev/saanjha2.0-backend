package com.saanjha.modules.team.repository;

import com.saanjha.modules.team.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the V10 migration's three partial unique indexes against a real
 * PostgreSQL instance: at most one live membership per (team, user), exactly
 * one active Lead per team, and the source-reference idempotency guard.
 */
@DataJpaTest
@Testcontainers
class TeamRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private TeamRepository teamRepository;
    @Autowired private MembershipRepository membershipRepository;

    @Test
    void projectIdUniqueness_blocksSecondTeamForSameProject() {
        UUID projectId = UUID.randomUUID();
        teamRepository.save(newTeam(projectId));

        assertThatThrownBy(() -> {
            teamRepository.save(newTeam(projectId));
            teamRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void singleActiveLead_isEnforcedAtTheDatabaseLevel() {
        Team team = teamRepository.save(newTeam(UUID.randomUUID()));
        membershipRepository.save(newMembership(team, UUID.randomUUID(), MembershipRole.LEAD, MembershipStatus.ACTIVE));

        assertThatThrownBy(() -> {
            membershipRepository.save(newMembership(team, UUID.randomUUID(), MembershipRole.LEAD, MembershipStatus.ACTIVE));
            membershipRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneLiveMembershipPerUser_butMultipleTerminalRowsAreAllowed() {
        Team team = teamRepository.save(newTeam(UUID.randomUUID()));
        UUID userId = UUID.randomUUID();

        Membership first = newMembership(team, userId, MembershipRole.MEMBER, MembershipStatus.ACTIVE);
        first.setStatus(MembershipStatus.LEFT);
        membershipRepository.save(first);

        Membership rejoined = newMembership(team, userId, MembershipRole.MEMBER, MembershipStatus.ACTIVE);
        rejoined.setJoinedVia(MembershipSource.REJOINED);
        Membership saved = membershipRepository.save(rejoined);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void liveMembershipUniqueness_blocksSecondActiveRowForSameUser() {
        Team team = teamRepository.save(newTeam(UUID.randomUUID()));
        UUID userId = UUID.randomUUID();
        membershipRepository.save(newMembership(team, userId, MembershipRole.MEMBER, MembershipStatus.ACTIVE));

        assertThatThrownBy(() -> {
            membershipRepository.save(newMembership(team, userId, MembershipRole.MEMBER, MembershipStatus.ACTIVE));
            membershipRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sourceReferenceUniqueness_preventsDuplicateEventFromSeatingTwoMemberships() {
        Team team = teamRepository.save(newTeam(UUID.randomUUID()));
        UUID sourceReferenceId = UUID.randomUUID();

        Membership first = newMembership(team, UUID.randomUUID(), MembershipRole.MEMBER, MembershipStatus.ACTIVE);
        first.setSourceReferenceId(sourceReferenceId);
        membershipRepository.save(first);

        assertThatThrownBy(() -> {
            Membership duplicate = newMembership(team, UUID.randomUUID(), MembershipRole.MEMBER, MembershipStatus.ACTIVE);
            duplicate.setSourceReferenceId(sourceReferenceId);
            membershipRepository.save(duplicate);
            membershipRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Team newTeam(UUID projectId) {
        Team team = new Team();
        team.setProjectId(projectId);
        team.setStatus(TeamStatus.CREATED);
        team.setSettingsJson("{}");
        return team;
    }

    private Membership newMembership(Team team, UUID userId, MembershipRole role, MembershipStatus status) {
        Membership membership = new Membership();
        membership.setTeam(team);
        membership.setUserId(userId);
        membership.setRole(role);
        membership.setStatus(status);
        membership.setJoinedVia(MembershipSource.MANUAL);
        return membership;
    }
}
