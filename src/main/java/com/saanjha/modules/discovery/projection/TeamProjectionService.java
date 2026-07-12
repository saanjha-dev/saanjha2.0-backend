package com.saanjha.modules.discovery.projection;

import com.saanjha.modules.discovery.entity.TeamSearchDocument;
import com.saanjha.modules.discovery.repository.TeamSearchDocumentRepository;
import com.saanjha.modules.team.event.TeamEvents.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Lightweight read model of a team, projected from Team's own events. */
@Service
@RequiredArgsConstructor
public class TeamProjectionService {

    private final TeamSearchDocumentRepository repository;

    public void applyCreated(TeamCreatedEvent event) {
        TeamSearchDocument doc = repository.findById(event.teamId()).orElseGet(TeamSearchDocument::new);
        doc.setTeamId(event.teamId());
        doc.setProjectId(event.projectId());
        doc.setFounderUserId(event.founderUserId());
        doc.setStatus("ACTIVE");
        doc.setCurrentSize(1);
        repository.save(doc);
    }

    public void applyMemberJoined(MemberJoinedEvent event) {
        updateSize(event.teamId(), event.currentTeamSize());
    }

    public void applyMemberLeft(MemberLeftEvent event) {
        updateSize(event.teamId(), event.currentTeamSize());
    }

    public void applyMemberRemoved(MemberRemovedEvent event) {
        updateSize(event.teamId(), event.currentTeamSize());
    }

    public void applyLocked(TeamLockedEvent event) {
        setStatus(event.teamId(), "LOCKED");
    }

    public void applyUnlocked(TeamUnlockedEvent event) {
        setStatus(event.teamId(), "ACTIVE");
    }

    public void applyArchived(TeamArchivedEvent event) {
        setStatus(event.teamId(), "ARCHIVED");
    }

    public void applyDissolved(TeamDissolvedEvent event) {
        setStatus(event.teamId(), "DISSOLVED");
    }

    private void updateSize(java.util.UUID teamId, int size) {
        repository.findById(teamId).ifPresent(doc -> {
            doc.setCurrentSize(size);
            repository.save(doc);
        });
    }

    private void setStatus(java.util.UUID teamId, String status) {
        repository.findById(teamId).ifPresent(doc -> {
            doc.setStatus(status);
            repository.save(doc);
        });
    }
}
