package com.saanjha.modules.chat.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Observability for the WebSocket layer (module brief's "OBSERVABILITY"
 * section: "WebSocket sessions" is one of the explicitly required
 * measurements). Message throughput/latency/reaction-latency/search-latency
 * are registered inline via {@code MeterRegistry.counter(...)}/{@code
 * .timer(...)} at their call sites in the relevant service (same
 * lazy-registration convention Notification's {@code
 * NotificationDispatchService} already uses) - this class exists
 * specifically for the one metric (session count) that has no natural call
 * site of its own, since it's a property of the connection lifecycle, not
 * of any single service method.
 */
@Configuration
public class ChatMetricsConfig {

    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public ChatMetricsConfig(MeterRegistry meterRegistry) {
        meterRegistry.gauge("chat.websocket.sessions.active", activeSessions);
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        activeSessions.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        activeSessions.updateAndGet(current -> Math.max(0, current - 1));
    }
}
