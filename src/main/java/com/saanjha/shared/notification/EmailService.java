package com.saanjha.shared.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async // CRITICAL: Executes in a separate thread pool
    public void sendOtpEmail(String to, String otp, String purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            
            String subject = purpose.equals("PASSWORD_RESET") ? "Saanjha Password Reset" : "Verify your Saanjha Account";
            helper.setSubject(subject);
            
            String htmlContent = String.format(
                "<div style='font-family: Arial; padding: 20px;'>" +
                "<h2>Saanjha Security</h2>" +
                "<p>Your one-time password is: <strong style='font-size: 24px; color: #4F46E5;'>%s</strong></p>" +
                "<p>This code will expire in 5 minutes. Do not share it with anyone.</p>" +
                "</div>", otp);
                
            helper.setText(htmlContent, true);
            mailSender.send(message);
            
            log.info("Successfully dispatched {} email to {}", purpose, to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
        }
    }
}