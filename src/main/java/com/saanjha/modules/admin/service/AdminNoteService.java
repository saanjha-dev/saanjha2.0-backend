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

    @Transactional
    public AdminNote addNote(UUID authorId, ModerationTargetType targetType, UUID targetId, String note) {
        AdminNote entity = new AdminNote();
        entity.setAuthorId(authorId);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setNote(note);
        return noteRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AdminNote> getNotes(ModerationTargetType targetType, UUID targetId) {
        return noteRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }
}
