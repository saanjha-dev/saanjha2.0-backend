package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.dto.ChatResponseDTOs.ReactionSummaryResponse;
import com.saanjha.modules.chat.entity.Message;
import com.saanjha.modules.chat.entity.Reaction;
import com.saanjha.modules.chat.event.ChatEvents.ReactionAddedEvent;
import com.saanjha.modules.chat.event.ChatEvents.ReactionRemovedEvent;
import com.saanjha.modules.chat.repository.MessageRepository;
import com.saanjha.modules.chat.repository.ReactionRepository;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void addReaction(UUID conversationId, UUID messageId, UUID userId, String emoji) {
        getLiveMessageOrThrow(messageId, conversationId);
        try {
            Reaction reaction = new Reaction();
            reaction.setMessageId(messageId);
            reaction.setUserId(userId);
            reaction.setEmoji(emoji);
            reactionRepository.save(reaction);
        } catch (DataIntegrityViolationException alreadyReacted) {
            return; // idempotent - "unique per user" constraint already satisfied
        }
        meterRegistry.counter("chat.reaction.added").increment();
        eventPublisher.publishEvent(new ReactionAddedEvent(messageId, conversationId, userId, emoji, Instant.now()));
    }

    @Transactional
    public void removeReaction(UUID conversationId, UUID messageId, UUID userId, String emoji) {
        getLiveMessageOrThrow(messageId, conversationId);
        Optional<Reaction> reaction = reactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);
        if (reaction.isEmpty()) {
            return; // idempotent
        }
        reactionRepository.delete(reaction.get());
        eventPublisher.publishEvent(new ReactionRemovedEvent(messageId, conversationId, userId, emoji, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<ReactionSummaryResponse> summarizeForMessage(UUID messageId, UUID viewerId) {
        List<Reaction> reactions = reactionRepository.findByMessageId(messageId);
        Map<String, List<Reaction>> byEmoji = reactions.stream().collect(Collectors.groupingBy(Reaction::getEmoji));
        return byEmoji.entrySet().stream()
                .map(entry -> new ReactionSummaryResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().anyMatch(r -> r.getUserId().equals(viewerId))))
                .toList();
    }

    private Message getLiveMessageOrThrow(UUID messageId, UUID conversationId) {
        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Message not found in this conversation."));
        if (message.isDeleted()) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_ALREADY_DELETED);
        }
        return message;
    }
}
