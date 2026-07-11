package com.saanjha.modules.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A persisted, human-readable mirror of what Resilience4j's in-memory
 * circuit breaker registry already knows, kept for two reasons Resilience4j
 * itself doesn't cover: (1) visibility across an app restart, and (2) a
 * queryable table the admin endpoint / a future dashboard can read without
 * reaching into Resilience4j's Java API. {@code ProviderHealthTracker}
 * updates this row after every attempt; {@code ProviderChainResolver} reads
 * it only to decide provider *ordering* preference within a channel's chain
 * (a provider with recent failures sorts after a healthier one) - actual
 * failover-on-failure is still Resilience4j's circuit breaker, not this
 * table, so a stale row here can never block real dispatch, only bias its
 * ordering.
 */
@Entity
@Table(name = "ntf_provider_health", schema = "ntf")
public class ProviderHealth {

    @Id
    @Column(name = "provider_channel_key", length = 40)
    private String providerChannelKey; // e.g. "NOTIFICATION_HUB:EMAIL"

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private ProviderName provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 15)
    private NotificationChannel channel;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "total_attempts", nullable = false)
    private long totalAttempts = 0;

    @Column(name = "total_failures", nullable = false)
    private long totalFailures = 0;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProviderHealth() {
        // JPA
    }

    public static ProviderHealth init(ProviderName provider, NotificationChannel channel) {
        ProviderHealth h = new ProviderHealth();
        h.providerChannelKey = provider.name() + ":" + channel.name();
        h.provider = provider;
        h.channel = channel;
        return h;
    }

    public void recordSuccess() {
        this.consecutiveFailures = 0;
        this.totalAttempts++;
        this.lastSuccessAt = Instant.now();
        this.updatedAt = this.lastSuccessAt;
    }

    public void recordFailure(String error) {
        this.consecutiveFailures++;
        this.totalAttempts++;
        this.totalFailures++;
        this.lastFailureAt = Instant.now();
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        this.updatedAt = this.lastFailureAt;
    }

    public String getProviderChannelKey() { return providerChannelKey; }
    public ProviderName getProvider() { return provider; }
    public NotificationChannel getChannel() { return channel; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public long getTotalAttempts() { return totalAttempts; }
    public long getTotalFailures() { return totalFailures; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public Instant getLastFailureAt() { return lastFailureAt; }
    public String getLastError() { return lastError; }
    public Instant getUpdatedAt() { return updatedAt; }
}
