package com.saanjha.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(EventPublisherService.class);
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Dispatches internal domain events.
     * Future Architecture Note: This is the exact injection point to implement
     * the Transactional Outbox Pattern for guaranteed RabbitMQ delivery.
     *
     * @param event The domain event payload
     */
    public void publish(Object event) {
        if (event == null) {
            log.warn("Attempted to publish a null event. Ignoring.");
            return;
        }

        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        eventPublisher.publishEvent(event);
    }
}