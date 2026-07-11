package com.saanjha.modules.notification.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications/webhooks")
@RequiredArgsConstructor
@Tag(name = "10. Notification", description = "Inbound provider delivery-status callbacks")
public class ProviderWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ProviderWebhookController.class);

    private final ProviderCallbackService callbackService;
    private final ObjectMapper objectMapper;

    @Value("${notification.hub.callback-signing-secret}")
    private String signingSecret;

    // A standard tolerance window (e.g., 5 minutes) to reject old, replayed requests
    private static final long TOLERANCE_IN_SECONDS = 300;

    @PostMapping("/notificationhub")
    @Operation(summary = "NotificationHub Delivery Callback", description = "Verifies HMAC-SHA256 signature using Timestamp + Body.")
    public ResponseEntity<Void> notificationHubCallback(
            @RequestHeader(value = "X-NotificationHub-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp, // Extract timestamp header
            @org.springframework.web.bind.annotation.RequestBody String rawBody) {

        if (signature == null || timestamp == null) {
            log.warn("Rejected NotificationHub callback: Missing signature or timestamp headers");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!verifySignature(rawBody, timestamp, signature)) {
            log.warn("Rejected NotificationHub callback: Invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            JsonNode json = objectMapper.readTree(rawBody);
            UUID deliveryId = UUID.fromString(json.get("deliveryId").asText());
            String status = json.get("status").asText();
            String errorMessage = json.hasNonNull("errorMessage") ? json.get("errorMessage").asText() : null;

            callbackService.apply(new ProviderCallbackPayload(deliveryId, status, errorMessage));
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            log.error("Malformed NotificationHub callback body", ex);
            return ResponseEntity.ok().build();
        }
    }

    private boolean verifySignature(String rawBody, String timestamp, String providedSignature) {
        try {
            // Optional but highly recommended: Protect against replay attacks
            long timestampLong = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestampLong) > TOLERANCE_IN_SECONDS) {
                log.warn("Webhook timestamp is outside of the tolerance zone.");
                return false;
            }

            // Reconstruct the exact string the Hub signed: "timestamp.body"
            String dataToSign = timestamp + "." + (rawBody == null ? "" : rawBody);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            byte[] expected = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expected);

            // Constant-time comparison
            return MessageDigest.isEqual(
                    expectedHex.getBytes(StandardCharsets.UTF_8),
                    providedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            log.error("Signature verification failed unexpectedly", ex);
            return false;
        }
    }
}