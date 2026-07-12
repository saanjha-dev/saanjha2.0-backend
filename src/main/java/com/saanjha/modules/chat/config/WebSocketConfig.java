package com.saanjha.modules.chat.config;

import com.saanjha.modules.chat.websocket.ChatChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket transport for message delivery, typing, presence,
 * reactions, and read-receipt fan-out (module brief's "WEBSOCKET" section -
 * REST continues to own history/search/CRUD/settings/pinned/attachments,
 * see the REST controllers in {@code chat.controller}).
 *
 * KNOWN SCALING LIMITATION (flagged, not solved here - see Future Extension
 * Points in the final report): {@code enableSimpleBroker} is the in-memory
 * STOMP broker, which only fans out to subscribers connected to *this*
 * application instance. The platform's current topology (Section J of the
 * MES: one Spring Boot instance on VPS 1) makes this correct today. Horizontal
 * scaling to multiple app instances would require relaying through a real
 * broker RabbitMQ is already a dependency in this project, unused
 * (TD3) and STOMP-capable - swapping {@code enableSimpleBroker} for {@code
 * enableStompBrokerRelay} at that point is a config change, not a redesign,
 * because every destination below is already broker-topic-shaped.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatChannelInterceptor chatChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*"); // CORS origin policy is enforced by SecurityConfig for REST;
                                                 // mirrored loosely here since the real gate is the CONNECT-frame JWT check.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatChannelInterceptor);
    }
}
