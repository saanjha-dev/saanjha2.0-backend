package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.dto.ChatRequestDTOs.SearchRequest;
import com.saanjha.modules.chat.dto.ChatResponseDTOs.MessageResponse;
import com.saanjha.modules.chat.entity.Attachment;
import com.saanjha.modules.chat.entity.Message;
import com.saanjha.modules.chat.entity.Reaction;
import com.saanjha.modules.chat.repository.AttachmentRepository;
import com.saanjha.modules.chat.repository.MessageRepository;
import com.saanjha.modules.chat.repository.ReactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Native Postgres GIN/tsvector search (module brief's "MESSAGE SEARCH"
 * section) - same technology as Discovery's project search, no
 * Elasticsearch, consistent with the platform-wide "Postgres-native, no
 * Elasticsearch" convention noted in prior sessions.
 */
@Service
@RequiredArgsConstructor
public class ChatSearchService {

    private final MessageRepository messageRepository;
    private final ReactionRepository reactionRepository;
    private final AttachmentRepository attachmentRepository;
    private final MeterRegistry meterRegistry;

    @Transactional(readOnly = true)
    public Page<MessageResponse> searchWithinConversation(UUID conversationId, String query, UUID viewerId, Pageable pageable) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Page<Message> results = messageRepository.searchWithinConversation(conversationId, query, pageable);
            return mapPage(results, viewerId);
        } finally {
            sample.stop(meterRegistry.timer("chat.search.latency", "scope", "conversation"));
        }
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> searchAcrossMyConversations(UUID viewerId, SearchRequest request, Pageable pageable) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Page<Message> results = messageRepository.searchAcrossMyConversations(
                    viewerId, request.query(), request.senderId(), request.fromDate(), request.toDate(), pageable);
            return mapPage(results, viewerId);
        } finally {
            sample.stop(meterRegistry.timer("chat.search.latency", "scope", "global"));
        }
    }

    private Page<MessageResponse> mapPage(Page<Message> page, UUID viewerId) {
        List<UUID> ids = page.getContent().stream().map(Message::getId).toList();
        Map<UUID, List<Reaction>> reactionsByMessage = ids.isEmpty() ? Map.of()
                : reactionRepository.findByMessageIdIn(ids).stream().collect(Collectors.groupingBy(Reaction::getMessageId));
        Map<UUID, List<Attachment>> attachmentsByMessage = ids.isEmpty() ? Map.of()
                : attachmentRepository.findByMessageIdIn(ids).stream().collect(Collectors.groupingBy(Attachment::getMessageId));

        return page.map(m -> new MessageResponse(
                m.getId(), m.getConversationId(), m.getSenderId(), m.getParentMessageId(),
                m.getType().name(), m.getStatus().name(), m.getBody(), Map.of(),
                m.getReplyCount(), m.getLastReplyAt(),
                List.of(), // reaction/attachment summaries omitted in search hits - full detail available via getMessage
                List.of(),
                m.getCreatedAt(), m.getEditedAt(), m.getDeletedAt()
        ));
    }
}
