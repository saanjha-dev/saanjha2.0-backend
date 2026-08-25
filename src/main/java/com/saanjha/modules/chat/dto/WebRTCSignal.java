package com.saanjha.modules.chat.dto;

import java.util.UUID;
import java.util.Map;

/**
 * Payload for WebRTC signaling over STOMP.
 * Type can be: CALL_START, CALL_ACCEPT, CALL_REJECT, CALL_END, OFFER, ANSWER, ICE_CANDIDATE
 */
public record WebRTCSignal(
    UUID senderId,
    String type,
    Map<String, Object> payload,
    String callType // "video" or "voice"
) {}
