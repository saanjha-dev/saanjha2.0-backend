package com.saanjha.modules.notification.config;

import com.notificationhub.sdk.NotificationHubClient;
import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.provider.ConsoleProvider;
import com.saanjha.modules.notification.provider.NotificationHubProvider;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The composition root for the provider layer. Two things live here and
 * nowhere else:
 * <ol>
 *   <li>the {@link NotificationHubClient} bean - the single point where SDK
 *       credentials are read from config;</li>
 *   <li>construction of the per-channel {@link NotificationHubProvider} and
 *       {@link ConsoleProvider} instances, which can't be {@code @Component}-
 *       scanned because their constructors are parameterized by channel.</li>
 * </ol>
 * Every other provider ({@code SmtpEmailProvider}, {@code
 * DirectWebhookProvider}, {@code InAppStoreProvider}) is a normal singleton
 * {@code @Component} and needs no entry here.
 * <p>
 * Deliberately one {@code @Bean} method per channel rather than a single
 * method returning a {@code List<NotificationHubProvider>}: Spring's
 * collection-autowiring (used by {@code ProviderChainResolver}'s
 * {@code List<NotificationProvider>} constructor parameter) only gathers
 * individual beans assignable to the target type - a single bean that IS a
 * List would be invisible to it, not auto-unpacked.
 */
@Configuration
public class NotificationProviderConfig {

    @Bean
    public NotificationHubClient notificationHubClient(
            @Value("${notification.hub.api-key}") String apiKey,
            @Value("${notification.hub.api-secret}") String apiSecret,
            @Value("${notification.hub.base-url:}") String baseUrl) {

        // FIX: Instantiate the Builder directly using 'new'
        NotificationHubClient.Builder builder = new NotificationHubClient.Builder()
                .apiKey(apiKey)
                .apiSecret(apiSecret);

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    @Bean
    public NotificationHubProvider notificationHubEmailProvider(NotificationHubClient client, CircuitBreakerRegistry cb, RetryRegistry retry, BulkheadRegistry bh) {
        return new NotificationHubProvider(client, NotificationChannel.EMAIL, cb, retry, bh);
    }

    @Bean
    public NotificationHubProvider notificationHubSmsProvider(NotificationHubClient client, CircuitBreakerRegistry cb, RetryRegistry retry, BulkheadRegistry bh) {
        return new NotificationHubProvider(client, NotificationChannel.SMS, cb, retry, bh);
    }

    @Bean
    public NotificationHubProvider notificationHubPushProvider(NotificationHubClient client, CircuitBreakerRegistry cb, RetryRegistry retry, BulkheadRegistry bh) {
        return new NotificationHubProvider(client, NotificationChannel.PUSH, cb, retry, bh);
    }

    // Deliberately no notificationHubInAppProvider bean: IN_APP is intrinsically local to this
    // module (InAppStoreProvider IS the delivery - see its javadoc), never routed externally.

    @Bean
    public NotificationHubProvider notificationHubWebhookProvider(NotificationHubClient client, CircuitBreakerRegistry cb, RetryRegistry retry, BulkheadRegistry bh) {
        return new NotificationHubProvider(client, NotificationChannel.WEBHOOK, cb, retry, bh);
    }

    @Bean
    public ConsoleProvider consoleEmailProvider() { return new ConsoleProvider(NotificationChannel.EMAIL); }

    @Bean
    public ConsoleProvider consoleSmsProvider() { return new ConsoleProvider(NotificationChannel.SMS); }

    @Bean
    public ConsoleProvider consolePushProvider() { return new ConsoleProvider(NotificationChannel.PUSH); }

    @Bean
    public ConsoleProvider consoleInAppProvider() { return new ConsoleProvider(NotificationChannel.IN_APP); }

    @Bean
    public ConsoleProvider consoleWebhookProvider() { return new ConsoleProvider(NotificationChannel.WEBHOOK); }
}
