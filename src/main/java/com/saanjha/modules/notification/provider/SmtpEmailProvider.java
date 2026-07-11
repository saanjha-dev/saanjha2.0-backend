package com.saanjha.modules.notification.provider;

import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.ProviderName;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * EMAIL's fallback provider when NotificationHub is unavailable. Uses the
 * {@link JavaMailSender} bean the app already configures via
 * {@code spring.mail.*} (this codebase's existing {@code shared.notification.EmailService}
 * uses the same bean for OTP delivery) - reusing shared infrastructure rather
 * than standing up a second SMTP client. Deliberately its own small class
 * rather than a dependency on {@code EmailService}: that class's public API
 * is OTP-shaped (fixed subject logic, fire-and-forget {@code @Async void}),
 * which doesn't fit a provider that must report success/failure per-attempt
 * back into {@code ProviderAttempt}.
 */
@Component
public class SmtpEmailProvider implements NotificationProvider {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public SmtpEmailProvider(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public ProviderName name() {
        return ProviderName.SMTP;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    @CircuitBreaker(name = "smtp")
    @Retry(name = "smtp")
    public ProviderDispatchResult send(ProviderDispatchRequest request) throws ProviderDispatchException {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(request.recipientAddress());
            helper.setSubject(request.subject() != null ? request.subject() : "Saanjha notification");

            String actionHtml = request.actionUrl() != null
                    ? "<p><a href=\"" + request.actionUrl() + "\">View in Saanjha</a></p>"
                    : "";
            String html = "<div style='font-family: Arial, sans-serif; padding: 20px;'>"
                    + "<p>" + request.body() + "</p>" + actionHtml + "</div>";
            helper.setText(html, true);

            mailSender.send(message);
            return ProviderDispatchResult.accepted(250, null);
        } catch (MessagingException ex) {
            // Malformed message construction - not a transport failure, won't
            // succeed on retry either.
            throw new ProviderDispatchException("Failed to build SMTP message: " + ex.getMessage(), true, ex);
        } catch (MailException ex) {
            // Connection refused, auth failure, timeout, etc. - genuinely
            // retryable / worth falling through to CONSOLE if exhausted.
            throw new ProviderDispatchException("SMTP send failed: " + ex.getMessage(), false, ex);
        }
    }
}
