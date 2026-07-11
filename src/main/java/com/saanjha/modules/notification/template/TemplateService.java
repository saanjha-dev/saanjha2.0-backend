package com.saanjha.modules.notification.template;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.NotificationTemplate;
import com.saanjha.modules.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the active {@link NotificationTemplate} for (eventType, channel,
 * locale), renders it, and falls back to a generic, always-available
 * message if no DB row matches - dispatch must never fail just because an
 * admin hasn't authored a template for a given event/channel/locale
 * combination yet (this is the same "degrade gracefully, never block" spirit
 * as the provider fallback chain, applied to content instead of transport).
 * <p>
 * Cache is a plain {@code ConcurrentHashMap} with a short TTL per entry
 * (read on every dispatch, so it needs to be cheap) rather than an
 * invalidate-on-write cache: there is no template-editing endpoint in v1
 * (see the module's Future Extension Points), so there is nothing to
 * invalidate on yet; a bounded staleness window is a deliberate, simple
 * placeholder for when one exists.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000;

    private final NotificationTemplateRepository templateRepository;
    private final TemplateEngine engine;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record RenderedContent(String subject, String body, String actionUrl) {}

    public RenderedContent render(String eventType, NotificationChannel channel, String locale, Map<String, Object> variables) {
        NotificationTemplate template = lookup(eventType, channel, locale);
        boolean htmlBody = channel == NotificationChannel.EMAIL || channel == NotificationChannel.WEBHOOK;

        if (template != null) {
            return new RenderedContent(
                    engine.render(template.getSubjectTemplate(), variables, false),
                    engine.render(template.getBodyTemplate(), variables, htmlBody),
                    engine.render(template.getActionUrlTemplate(), variables, false)
            );
        }

        // Fallback: never block dispatch on a missing template row.
        String title = String.valueOf(variables.getOrDefault("title", humanize(eventType)));
        return new RenderedContent(title, title, null);
    }

    private NotificationTemplate lookup(String eventType, NotificationChannel channel, String locale) {
        String key = eventType + ":" + channel + ":" + locale;
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.template;
        }
        NotificationTemplate template = templateRepository
                .findByEventTypeAndChannelAndLocaleAndActiveTrue(eventType, channel, locale)
                .or(() -> locale.equals("en") ? java.util.Optional.empty()
                        : templateRepository.findByEventTypeAndChannelAndLocaleAndActiveTrue(eventType, channel, "en"))
                .orElse(null);
        cache.put(key, new CacheEntry(template, Instant.now().plusMillis(CACHE_TTL_MILLIS)));
        return template;
    }

    private static String humanize(String eventType) {
        String[] words = eventType.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private record CacheEntry(NotificationTemplate template, Instant expiresAt) {}
}
