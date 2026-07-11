package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderHealth;
import com.saanjha.modules.notification.entity.ProviderName;
import com.saanjha.modules.notification.repository.ProviderHealthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderChainResolverTest {

    @Mock private ProviderHealthRepository healthRepository;

    private FakeProvider hub;
    private FakeProvider smtp;
    private FakeProvider console;
    private ProviderChainResolver resolver;

    @BeforeEach
    void setUp() {
        hub = new FakeProvider(ProviderName.NOTIFICATION_HUB, NotificationChannel.EMAIL);
        smtp = new FakeProvider(ProviderName.SMTP, NotificationChannel.EMAIL);
        console = new FakeProvider(ProviderName.CONSOLE, NotificationChannel.EMAIL);
        resolver = new ProviderChainResolver(List.of(console, hub, smtp), healthRepository); // deliberately unordered input
        resolver.buildChains();
    }

    @Test
    void baseOrder_hubFirst_thenDirectFallback_consoleAlwaysLast() {
        when(healthRepository.findAllByOrderByChannelAscConsecutiveFailuresAsc()).thenReturn(List.of());

        List<NotificationProvider> chain = resolver.resolve(NotificationChannel.EMAIL);

        assertThat(chain).extracting(NotificationProvider::name)
                .containsExactly(ProviderName.NOTIFICATION_HUB, ProviderName.SMTP, ProviderName.CONSOLE);
    }

    @Test
    void consoleNeverMovesAheadOfAStruggling_butStillTiered_provider() {
        // Even if NotificationHub has a terrible health record, it must still sort before CONSOLE -
        // tier ordering is a hard floor, health only re-sorts *within* a tier.
        ProviderHealth badHubHealth = ProviderHealth.init(ProviderName.NOTIFICATION_HUB, NotificationChannel.EMAIL);
        for (int i = 0; i < 50; i++) badHubHealth.recordFailure("down");
        when(healthRepository.findAllByOrderByChannelAscConsecutiveFailuresAsc()).thenReturn(List.of(badHubHealth));

        List<NotificationProvider> chain = resolver.resolve(NotificationChannel.EMAIL);

        assertThat(chain.get(chain.size() - 1).name()).isEqualTo(ProviderName.CONSOLE);
        // NOTIFICATION_HUB is alone in tier 0 for EMAIL (SMTP is a lower tier, CONSOLE lower still),
        // so no amount of bad health moves it out of first place - health only re-sorts *within* a tier.
        assertThat(chain.get(0).name()).isEqualTo(ProviderName.NOTIFICATION_HUB);
    }

    @Test
    void unknownChannel_returnsEmptyList() {
        assertThat(resolver.resolve(NotificationChannel.WEBHOOK)).isEmpty();
    }

    private static class FakeProvider implements NotificationProvider {
        private final ProviderName name;
        private final NotificationChannel channel;

        FakeProvider(ProviderName name, NotificationChannel channel) {
            this.name = name;
            this.channel = channel;
        }

        @Override public ProviderName name() { return name; }
        @Override public NotificationChannel channel() { return channel; }
        @Override public ProviderDispatchResult send(ProviderDispatchRequest request) { return ProviderDispatchResult.accepted(200, null); }
    }
}
