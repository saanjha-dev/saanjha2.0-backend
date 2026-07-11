package com.saanjha.modules.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail, one row per actual provider call (module brief:
 * "Each transition audited"). Never updated after insert - same immutability
 * discipline as {@code PortfolioEntry}/{@code ContributionLedgerEntry}.
 */
@Entity
@Table(name = "ntf_provider_attempts", schema = "ntf", indexes = {
        @Index(name = "idx_ntf_attempts_delivery", columnList = "delivery_id")
})
public class ProviderAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private ProviderName provider;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();

    protected ProviderAttempt() {
        // JPA
    }

    public static ProviderAttempt record(UUID deliveryId, ProviderName provider, int attemptNumber,
                                          boolean success, Integer statusCode, String errorMessage, long latencyMs) {
        ProviderAttempt a = new ProviderAttempt();
        a.deliveryId = deliveryId;
        a.provider = provider;
        a.attemptNumber = attemptNumber;
        a.success = success;
        a.statusCode = statusCode;
        a.errorMessage = errorMessage != null && errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
        a.latencyMs = latencyMs;
        a.attemptedAt = Instant.now();
        return a;
    }

    public UUID getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public ProviderName getProvider() { return provider; }
    public int getAttemptNumber() { return attemptNumber; }
    public boolean isSuccess() { return success; }
    public Integer getStatusCode() { return statusCode; }
    public String getErrorMessage() { return errorMessage; }
    public Long getLatencyMs() { return latencyMs; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
