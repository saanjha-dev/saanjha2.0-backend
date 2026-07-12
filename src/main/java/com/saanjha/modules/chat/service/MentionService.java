package com.saanjha.modules.chat.service;

import com.saanjha.modules.chat.entity.Mention;
import com.saanjha.modules.chat.entity.MemberStatus;
import com.saanjha.modules.chat.event.ChatEvents.MentionCreatedEvent;
import com.saanjha.modules.chat.repository.ConversationMemberRepository;
import com.saanjha.modules.chat.repository.MentionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code @<uuid>} mention tokens out of a message body (the frontend
 * is responsible for rendering an {@code @username} autocomplete and
 * substituting the resolved user id into the token before send - Chat never
 * does username-to-id resolution itself, since usernames are User's data).
 * Only mentions of users who are actually live conversation members are
 * accepted (module brief's "Mention validation" requirement) - mentioning a
 * non-member is silently dropped rather than erroring the whole send, so a
 * copy-pasted stale mention doesn't block an otherwise-valid message.
 */
@Service
@RequiredArgsConstructor
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile(
            "@\\[([0-9a-fA-F-]{36})]"); // @[<uuid>] token format

    private final MentionRepository mentionRepository;
    private final ConversationMemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void processMentions(UUID messageId, UUID conversationId, UUID authorId, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        Set<UUID> candidates = extractCandidateIds(body);
        if (candidates.isEmpty()) {
            return;
        }
        List<MemberStatus> live = List.of(MemberStatus.ACTIVE, MemberStatus.MUTED);
        Instant now = Instant.now();
        for (UUID candidate : candidates) {
            boolean isLiveMember = memberRepository.existsByConversationIdAndUserIdAndStatusIn(conversationId, candidate, live);
            if (!isLiveMember || candidate.equals(authorId)) {
                continue; // not a valid mention target - dropped silently, not an error
            }
            Mention mention = new Mention();
            mention.setMessageId(messageId);
            mention.setConversationId(conversationId);
            mention.setMentionedUserId(candidate);
            mentionRepository.save(mention);
            eventPublisher.publishEvent(new MentionCreatedEvent(messageId, conversationId, candidate, authorId, now));
        }
    }

    private Set<UUID> extractCandidateIds(String body) {
        Matcher matcher = MENTION_PATTERN.matcher(body);
        Set<UUID> ids = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            try {
                ids.add(UUID.fromString(matcher.group(1)));
            } catch (IllegalArgumentException ignored) {
                // malformed token - not a mention
            }
        }
        return ids;
    }
}
