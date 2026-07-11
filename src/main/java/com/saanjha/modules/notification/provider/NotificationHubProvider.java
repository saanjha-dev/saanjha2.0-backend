package com.saanjha.modules.notification.provider;

import com.notificationhub.sdk.NotificationHubClient;
import com.notificationhub.sdk.client.NotificationApiClient.NotificationResponse;
import com.notificationhub.sdk.exception.NotificationHubException;
import com.notificationhub.sdk.model.ChannelType;
import com.notificationhub.sdk.model.NotificationRequest;
import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderName;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Wraps sumitshresht/notificationhub-java. This is the ONLY class in the
 * whole module (besides its own config bean) that imports an SDK class - see
 * the module brief's "Notification Module must never make the SDK a
 * dependency for business logic". Handles every channel: the SDK exposes one
 * generic {@code /api/v1/notifications} endpoint keyed by {@code ChannelType},
 * not one endpoint per channel.
 * <p>
 * One instance per channel is constructed manually by {@code
 * NotificationHubProviderConfig} (not component-scanned - the constructor is
 * parameterized by channel, which Spring can't autowire). Because these
 * instances are never Spring-proxied, Resilience4j's {@code @CircuitBreaker}/
 * {@code @Retry}/{@code @Bulkhead} annotations would silently do nothing here
 * (Spring AOP only intercepts calls made *through* a proxy) - so resilience
 * is applied programmatically via {@link Decorators} instead, using
 * registries injected at construction time. Each channel gets its own named
 * instance ({@code "notificationHub-EMAIL"}, {@code "notificationHub-SMS"},
 * ...), configured in application.yml, so one channel's outage trips only
 * its own circuit.
 */
public class NotificationHubProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(NotificationHubProvider.class);

    private final NotificationHubClient client;
    private final NotificationChannel channel;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public NotificationHubProvider(NotificationHubClient client, NotificationChannel channel,
                                    CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry,
                                    BulkheadRegistry bulkheadRegistry) {
        this.client = client;
        this.channel = channel;
        String instanceName = "notificationHub-" + channel.name();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName, "notificationHub");
        this.retry = retryRegistry.retry(instanceName, "notificationHub");
        this.bulkhead = bulkheadRegistry.bulkhead(instanceName, "notificationHub");
    }

    @Override
    public ProviderName name() {
        return ProviderName.NOTIFICATION_HUB;
    }

    @Override
    public NotificationChannel channel() {
        return channel;
    }

    @Override
    public ProviderDispatchResult send(ProviderDispatchRequest request) throws ProviderDispatchException {
        Supplier<ProviderDispatchResult> decorated = Decorators.ofSupplier(() -> doSend(request))
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .withRetry(retry)
                .decorate();

        Try<ProviderDispatchResult> result = Try.ofSupplier(decorated);
        if (result.isSuccess()) {
            return result.get();
        }
        Throwable cause = result.getCause();
        if (cause instanceof ProviderDispatchException pde) {
            throw pde;
        }
        throw new ProviderDispatchException("NotificationHub call failed: " + cause.getMessage(), false, cause);
    }

    private ProviderDispatchResult doSend(ProviderDispatchRequest request) {
        try {
            NotificationRequest.Builder builder = NotificationRequest.builder()
                    .addChannel(ChannelType.valueOf(channel.name()))
                    .subject(request.subject())
                    .message(request.body())
                    .addVariable("notificationId", request.notificationId().toString())
                    .addVariable("deliveryId", request.deliveryId().toString());

            if (request.actionUrl() != null) {
                builder.withActionUrl(request.actionUrl());
            }

            switch (channel) {
                case EMAIL -> builder.toEmail(request.recipientAddress());
                case SMS -> builder.toPhone(request.recipientAddress());
                case PUSH -> builder.toPushToken(request.recipientAddress());
                case WEBHOOK -> builder.toWebhook(request.recipientAddress());
                case IN_APP -> builder.toInApp(request.recipientAddress());
            }

            NotificationResponse response = client.notifications().sendWithIdempotency(builder.build(), request.deliveryId());
            return ProviderDispatchResult.accepted(202, response.notificationId() == null ? null : response.notificationId().toString());

        } catch (NotificationHubException.NotificationHubValidationException ex) {
            // Bad recipient address / malformed content - retrying against the
            // same or a different provider cannot fix this, so this is
            // permanent: NotificationDispatchService moves straight to the
            // next provider in the chain rather than burning retry attempts.
            // Resilience4j's Retry is configured to NOT retry on this
            // exception type (see application.yml ignore-exceptions) so this
            // still surfaces after exactly one attempt.
            throw new ProviderDispatchException("NotificationHub rejected the request as invalid: " + ex.getMessage(), true, ex);
        } catch (NotificationHubException.NotificationHubAuthException ex) {
            log.error("NotificationHub credentials rejected (HTTP {}) - check notification.hub.api-key/api-secret configuration", ex.getStatusCode(), ex);
            throw new ProviderDispatchException("NotificationHub authentication failed: " + ex.getMessage(), true, ex);
        } catch (NotificationHubException.NotificationHubRateLimitException ex) {
            throw new ProviderDispatchException("NotificationHub rate limit hit: " + ex.getMessage(), false, ex);
        } catch (NotificationHubException ex) {
            // Server errors (5xx) and network/serialization failures (statusCode 0) - retryable.
            throw new ProviderDispatchException("NotificationHub call failed: " + ex.getMessage(), false, ex);
        }
    }
}
