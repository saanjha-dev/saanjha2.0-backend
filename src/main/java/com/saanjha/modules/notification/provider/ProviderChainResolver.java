package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderHealth;
import com.saanjha.modules.notification.entity.ProviderName;
import com.saanjha.modules.notification.repository.ProviderHealthRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds and orders each channel's fallback chain (module brief: "Every
 * channel must support multiple providers" + "Provider Priority" +
 * "Automatic Failover"). Spring injects every {@link NotificationProvider}
 * bean that exists (the 5 per-channel {@code NotificationHubProvider}
 * instances, the singleton {@code SmtpEmailProvider}/{@code
 * DirectWebhookProvider}/{@code InAppStoreProvider}, and the 5 per-channel
 * {@code ConsoleProvider} instances - see {@code NotificationProviderConfig})
 * - this class only groups and orders them, it never constructs one.
 * <p>
 * Base order per channel is fixed at startup (NotificationHub first, a
 * channel-appropriate direct fallback second, CONSOLE always last).
 * {@link #resolve} then re-sorts stably by each provider's current {@code
 * consecutiveFailures} in {@link ProviderHealth} (ascending) - a provider
 * that's currently unhealthy sorts later within its tier, but CONSOLE never
 * moves ahead of a healthier real provider and a real provider never moves
 * behind CONSOLE. This is ordering *preference* only: the actual
 * skip-on-failure behavior during dispatch comes from {@code
 * NotificationDispatchService} trying each entry in order until one
 * succeeds, not from this class removing anything.
 */
@Component
public class ProviderChainResolver {

    private final List<NotificationProvider> allProviders;
    private final ProviderHealthRepository healthRepository;
    private final Map<NotificationChannel, List<NotificationProvider>> baseChains = new EnumMap<>(NotificationChannel.class);

    public ProviderChainResolver(List<NotificationProvider> allProviders, ProviderHealthRepository healthRepository) {
        this.allProviders = allProviders;
        this.healthRepository = healthRepository;
    }

    @PostConstruct
    void buildChains() {
        for (NotificationChannel channel : NotificationChannel.values()) {
            List<NotificationProvider> forChannel = allProviders.stream()
                    .filter(p -> p.channel() == channel)
                    .sorted(Comparator.comparingInt(p -> tierOf(p.name())))
                    .toList();
            baseChains.put(channel, forChannel);
        }
    }

    /** Returns the ordered list of providers to try for this channel, healthiest-first within each fixed tier. */
    public List<NotificationProvider> resolve(NotificationChannel channel) {
        List<NotificationProvider> base = baseChains.getOrDefault(channel, List.of());
        if (base.size() <= 1) {
            return base;
        }
        Map<String, Integer> failureCounts = new HashMap<>();
        for (ProviderHealth h : healthRepository.findAllByOrderByChannelAscConsecutiveFailuresAsc()) {
            if (h.getChannel() == channel) {
                failureCounts.put(h.getProvider().name(), h.getConsecutiveFailures());
            }
        }
        return base.stream()
                .sorted(Comparator
                        .<NotificationProvider>comparingInt(p -> tierOf(p.name()))
                        .thenComparingInt(p -> failureCounts.getOrDefault(p.name().name(), 0)))
                .toList();
    }

    /** Lower = tried earlier. CONSOLE is always last regardless of health. */
    private static int tierOf(ProviderName provider) {
        return switch (provider) {
            case NOTIFICATION_HUB -> 0;
            case SMTP, DIRECT_WEBHOOK, IN_APP_STORE -> 1;
            case CONSOLE -> 2;
        };
    }
}
