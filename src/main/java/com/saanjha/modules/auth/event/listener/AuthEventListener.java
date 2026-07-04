package com.saanjha.modules.auth.event.listener;

import com.saanjha.modules.auth.event.AuthEvents.OtpGeneratedEvent;
import com.saanjha.shared.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthEventListener {

    private final EmailService emailService;

    @EventListener
    public void handleOtpGenerated(OtpGeneratedEvent event) {
        System.out.println("EVENT RECEIVED: " + event.email() + " OTP=" + event.rawOtp());
        emailService.sendOtpEmail(event.email(), event.rawOtp(), event.purpose());
    }
}