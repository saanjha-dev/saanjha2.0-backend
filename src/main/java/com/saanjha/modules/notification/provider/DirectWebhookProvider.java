package com.saanjha.modules.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderName;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WEBHOOK's fallback provider: a direct HTTP POST to the recipient's URL,
 * entirely independent of the NotificationHub SDK - genuinely a different
 * code path, not a thin wrapper around the same client, so a total SDK/API
 * outage doesn't take webhook delivery down with it. Payload is HMAC-SHA256
 * signed (header {@code X-Saanjha-Signature}) using a per-deployment secret
 * (module brief's "Webhook verification" - the receiving side of that
 * concern, {@code ProviderWebhookController}, verifies inbound
 * delivery-status callbacks the same way).
 */
@Component
public class DirectWebhookProvider implements NotificationProvider {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${notification.webhook.signing-secret:changeme-webhook-secret}")
    private String signingSecret;

    @Override
    public ProviderName name() {
        return ProviderName.DIRECT_WEBHOOK;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEBHOOK;
    }

    @Override
    @CircuitBreaker(name = "directWebhook")
    @Retry(name = "directWebhook")
    public ProviderDispatchResult send(ProviderDispatchRequest request) throws ProviderDispatchException {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("notificationId", request.notificationId().toString());
            body.put("deliveryId", request.deliveryId().toString());
            body.put("subject", request.subject());
            body.put("message", request.body());
            body.put("actionUrl", request.actionUrl());

            String json = objectMapper.writeValueAsString(body);
            String signature = sign(json);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(request.recipientAddress()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Saanjha-Signature", signature)
                    .header("X-Saanjha-Delivery-Id", request.deliveryId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ProviderDispatchResult.accepted(response.statusCode(), null);
            }
            boolean permanent = response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 429;
            throw new ProviderDispatchException("Webhook endpoint returned HTTP " + response.statusCode(), permanent);

        } catch (ProviderDispatchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderDispatchException("Webhook dispatch failed: " + ex.getMessage(), false, ex);
        }
    }

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
