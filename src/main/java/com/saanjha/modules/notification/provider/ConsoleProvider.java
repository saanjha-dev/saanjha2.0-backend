package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The last link in every channel's fallback chain (module brief: "Never
 * allow provider failure to stop notification orchestration"). Logs at WARN
 * and reports success unconditionally - this is what makes it possible for
 * the whole external world (NotificationHub, SMTP, a webhook endpoint) to be
 * down simultaneously without a single {@code NotificationDelivery} ever
 * landing in FAILED/DLQ purely because nothing could physically reach the
 * user; it lands as "acknowledged, not actually delivered anywhere a human
 * will see it" instead, which is a materially different and much less
 * severe failure mode - visible in {@code ProviderAttempt} audit rows and in
 * the {@code ProviderHealth} table (CONSOLE usage spiking is itself an
 * alarm-worthy signal once real observability/alerting is wired to this
 * module's metrics).
 * <p>
 * One instance, registered for every channel, rather than five separate
 * classes - there is no channel-specific behavior here.
 */
public class ConsoleProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(ConsoleProvider.class);

    private final NotificationChannel channel;

    /** Constructed manually once per channel in {@link ProviderChainResolver}, not a Spring-managed singleton - see that class. */
    public ConsoleProvider(NotificationChannel channel) {
        this.channel = channel;
    }

    @Override
    public ProviderName name() {
        return ProviderName.CONSOLE;
    }

    @Override
    public NotificationChannel channel() {
        return channel;
    }

    @Override
    public ProviderDispatchResult send(ProviderDispatchRequest request) {
        log.warn("CONSOLE fallback engaged for channel={} delivery={} recipient={} - every real provider for this channel is currently exhausted or disabled. subject='{}' body='{}'",
                channel, request.deliveryId(), request.recipientAddress(), request.subject(), truncate(request.body()));
        return ProviderDispatchResult.accepted(200, "console-" + request.deliveryId());
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
