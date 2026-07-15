package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.AdminNote;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.repository.AdminNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Internal, append-only moderator context. See {@link AdminNote}'s javadoc for why this is distinct from Report/ModerationAction. */
@Service
@RequiredArgsConstructor
public class AdminNoteService {

    private final AdminNoteRepository noteRepository;
    private final AdminAuditService auditService;

    /**
     * FIX (hardening sprint, P0-4): this was the one real gap found after
     * checking every {@code @Transactional} mutating method across every
     * Admin service against the audit ledger - {@code addNote} saved the
     * note itself (which is self-attributing: authorId/targetType/targetId/
     * timestamp) but never recorded an entry in {@code AdminAuditLog}, so
     * "who left internal notes about which user" wasn't visible from the
     * audit trail itself, only from directly querying the notes table.
     */
    @Transactional
    public AdminNote addNote(UUID authorId, ModerationTargetType targetType, UUID targetId, String note) {
        AdminNote entity = new AdminNote();
        entity.setAuthorId(authorId);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setNote(note);
        AdminNote saved = noteRepository.save(entity);

        auditService.record(authorId, "ADMIN_NOTE_ADDED", targetType, targetId, null, null, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<AdminNote> getNotes(ModerationTargetType targetType, UUID targetId) {
        return noteRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }
}
