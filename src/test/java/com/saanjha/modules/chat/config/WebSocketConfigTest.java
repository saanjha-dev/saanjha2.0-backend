package com.saanjha.modules.chat.config;

import com.saanjha.modules.chat.websocket.ChatChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

/**
 * Regression guard for the hardening-sprint fix: the {@code /ws/chat} STOMP
 * endpoint must use the same environment-configured origin allow-list as
 * REST ({@code app.security.allowed-origins} / TD8/S6), never a hardcoded
 * {@code "*"}.
 */
class WebSocketConfigTest {

    @Test
    void registerStompEndpoints_usesConfiguredAllowedOrigins_notWildcard() {
        ChatChannelInterceptor interceptor = mock(ChatChannelInterceptor.class);
        WebSocketConfig config = new WebSocketConfig(interceptor);
        ReflectionTestUtils.setField(config, "allowedOrigins",
                List.of("https://app.saanjha.example", "https://staging.saanjha.example"));

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws/chat")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any(String[].class))).thenReturn(registration);

        config.registerStompEndpoints(registry);

        var captor = forClass(String[].class);
        verify(registration).setAllowedOriginPatterns(captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrder("https://app.saanjha.example", "https://staging.saanjha.example")
                .doesNotContain("*");
    }
}
