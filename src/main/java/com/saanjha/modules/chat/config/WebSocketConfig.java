package com.saanjha.modules.chat.config;

import com.saanjha.modules.chat.websocket.ChatChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

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
 *
 * FIX (hardening sprint, P0-1): the STOMP endpoint previously allowed
 * {@code "*"} origins with a comment that the JWT CONNECT check was "the
 * real gate." That's true for spoofed identity, but a wildcard origin still
 * lets any third-party page open a socket to this endpoint at all (e.g. to
 * probe for the presence/typing broadcast shape, or as one leg of a
 * multi-step attack against a victim who's separately tricked into leaking
 * their token). {@code SecurityConfig} already established a hard
 * no-wildcard-in-any-profile policy for REST (TD8/S6:
 * {@code app.security.allowed-origins}, backed by
 * {@code CORS_ALLOWED_ORIGINS}/{@code CORS_ALLOWED_ORIGINS_PROD}, with
 * startup failing loudly if unset) - this reuses that exact same
 * configuration property rather than introducing a second, WebSocket-only
 * origin list to keep in sync.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatChannelInterceptor chatChannelInterceptor;

    @Value("${app.security.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .withSockJS();
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
