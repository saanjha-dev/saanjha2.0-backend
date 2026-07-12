package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.entity.Message;
import com.saanjha.modules.chat.entity.PinnedMessage;
import com.saanjha.modules.chat.event.ChatEvents.PinnedMessageCreatedEvent;
import com.saanjha.modules.chat.repository.MessageRepository;
import com.saanjha.modules.chat.repository.PinnedMessageRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PinnedMessageService {

    private final PinnedMessageRepository pinnedMessageRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void pin(UUID conversationId, UUID messageId, UUID actingUserId) {
        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Message not found in this conversation."));
        if (message.isDeleted()) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_ALREADY_DELETED);
        }
        // Already-actively-pinned is a no-op, not an error - re-pinning the same
        // message twice in a race just leaves it pinned once (DB partial unique
        // index on message_id WHERE unpinned_at IS NULL enforces this too).
        if (pinnedMessageRepository.findByMessageIdAndUnpinnedAtIsNull(messageId).isPresent()) {
            return;
        }
        PinnedMessage pin = new PinnedMessage();
        pin.setConversationId(conversationId);
        pin.setMessageId(messageId);
        pin.setPinnedBy(actingUserId);
        pin.setPinnedAt(Instant.now());
        try {
            pinnedMessageRepository.save(pin);
        } catch (org.springframework.dao.DataIntegrityViolationException raceLoser) {
            return; // another concurrent pin won the unique index race
        }
        eventPublisher.publishEvent(new PinnedMessageCreatedEvent(conversationId, messageId, actingUserId, Instant.now()));
    }

    @Transactional
    public void unpin(UUID conversationId, UUID messageId, UUID actingUserId) {
        PinnedMessage pin = pinnedMessageRepository.findByMessageIdAndUnpinnedAtIsNull(messageId)
                .orElse(null);
        if (pin == null || !pin.getConversationId().equals(conversationId)) {
            return; // idempotent, and TD25-pattern scope check: refuse to unpin a pin
                    // that belongs to a different conversation than the one authorized against
        }
        pin.setUnpinnedAt(Instant.now());
        pin.setUnpinnedBy(actingUserId);
        pinnedMessageRepository.save(pin);
    }

    @Transactional(readOnly = true)
    public Page<PinnedMessage> listActive(UUID conversationId, Pageable pageable) {
        return pinnedMessageRepository.findByConversationIdAndUnpinnedAtIsNullOrderByPinnedAtDesc(conversationId, pageable);
    }
}
