package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.entity.ConversationMember;
import com.saanjha.modules.chat.entity.ReadReceipt;
import com.saanjha.modules.chat.event.ChatEvents.ReadReceiptUpdatedEvent;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.MessageRepository;
import com.saanjha.modules.chat.repository.ReadReceiptRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns both halves of "read": the O(1) cursor on {@link ConversationMember}
 * (unread badge, last-seen) and the per-message audit trail in {@code
 * cht_read_receipts} ("seen by" list on a message). Both are written in the
 * same transaction so they never drift.
 */
@Service
@RequiredArgsConstructor
public class ReadReceiptService {

    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final ReadReceiptRepository readReceiptRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void markReadThrough(UUID conversationId, UUID userId, UUID messageId) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_NOT_A_MEMBER));
        messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Message not found in this conversation."));

        Instant now = Instant.now();
        member.markReadThrough(messageId, now);
        memberRepository.save(member);

        if (!readReceiptRepository.existsByMessageIdAndUserId(messageId, userId)) {
            ReadReceipt receipt = new ReadReceipt();
            receipt.setConversationId(conversationId);
            receipt.setMessageId(messageId);
            receipt.setUserId(userId);
            receipt.setReadAt(now);
            readReceiptRepository.save(receipt);
        }

        eventPublisher.publishEvent(new ReadReceiptUpdatedEvent(conversationId, userId, messageId, now));
    }

    /** Called from MessageService on every send to fan out unread-count increments
     * to every other live member - a single bulk UPDATE, not a per-member load/save
     * loop, so it stays cheap on a busy channel with many members. */
    @Transactional
    public void incrementUnreadForOthers(UUID conversationId, UUID senderId) {
        memberRepository.incrementUnreadForOthers(conversationId, senderId);
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> whoHasRead(UUID messageId) {
        return readReceiptRepository.findByMessageId(messageId).stream().map(ReadReceipt::getUserId).toList();
    }
}
