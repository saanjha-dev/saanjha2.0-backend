package com.saanjha.modules.admin.service;

import com.saanjha.modules.admin.entity.AdminNote;
import com.saanjha.modules.admin.entity.ModerationTargetType;
import com.saanjha.modules.admin.repository.AdminNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * FIX (hardening sprint, P0-4): {@code addNote} was the one real gap found
 * after auditing every mutating method across every Admin service against
 * the audit ledger - it saved the note but never called
 * {@code AdminAuditService.record}. This proves the fix.
 */
@ExtendWith(MockitoExtension.class)
class AdminNoteServiceTest {

    @Mock private AdminNoteRepository noteRepository;
    @Mock private AdminAuditService auditService;

    private AdminNoteService adminNoteService;

    @BeforeEach
    void setUp() {
        adminNoteService = new AdminNoteService(noteRepository, auditService);
    }

    @Test
    void addNote_savesNoteAndRecordsAuditEntry() {
        UUID authorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(noteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminNote result = adminNoteService.addNote(authorId, ModerationTargetType.USER, targetId, "Flagged for repeated spam reports.");

        assertThat(result.getAuthorId()).isEqualTo(authorId);
        assertThat(result.getTargetId()).isEqualTo(targetId);

        verify(auditService).record(
                eq(authorId), eq("ADMIN_NOTE_ADDED"), eq(ModerationTargetType.USER), eq(targetId),
                isNull(), isNull(), isNull());
    }

    @Test
    void getNotes_returnsNotesForTarget_andNeverTouchesAuditLog() {
        UUID targetId = UUID.randomUUID();
        AdminNote note = new AdminNote();
        when(noteRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(ModerationTargetType.USER, targetId))
                .thenReturn(List.of(note));

        List<AdminNote> result = adminNoteService.getNotes(ModerationTargetType.USER, targetId);

        assertThat(result).containsExactly(note);
        verifyNoInteractions(auditService);
    }
}
