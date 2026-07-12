package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.entity.Draft;
import com.saanjha.modules.chat.repository.DraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** REST-managed, not WebSocket - a draft should survive a page reload
 * (unlike Typing, which is purely ephemeral). Autosave-on-blur/periodic
 * client-side calls upsert; nothing here is time-sensitive. */
@Service
@RequiredArgsConstructor
public class DraftService {

    private final DraftRepository draftRepository;

    @Transactional
    public void save(UUID conversationId, UUID userId, UUID parentMessageId, String body) {
        Optional<Draft> existing = parentMessageId == null
                ? draftRepository.findByConversationIdAndUserIdAndParentMessageIdIsNull(conversationId, userId)
                : draftRepository.findByConversationIdAndUserIdAndParentMessageId(conversationId, userId, parentMessageId);

        Draft draft = existing.orElseGet(Draft::new);
        draft.setConversationId(conversationId);
        draft.setUserId(userId);
        draft.setParentMessageId(parentMessageId);
        draft.setBody(body);
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public Optional<Draft> get(UUID conversationId, UUID userId, UUID parentMessageId) {
        return parentMessageId == null
                ? draftRepository.findByConversationIdAndUserIdAndParentMessageIdIsNull(conversationId, userId)
                : draftRepository.findByConversationIdAndUserIdAndParentMessageId(conversationId, userId, parentMessageId);
    }

    @Transactional
    public void clear(UUID conversationId, UUID userId, UUID parentMessageId) {
        if (parentMessageId == null) {
            draftRepository.deleteByConversationIdAndUserIdAndParentMessageIdIsNull(conversationId, userId);
        } else {
            draftRepository.deleteByConversationIdAndUserIdAndParentMessageId(conversationId, userId, parentMessageId);
        }
    }
}
