package com.saanjha.modules.auth.event.listener;

import com.saanjha.modules.auth.event.AuthEvents.OtpGeneratedEvent;
import com.saanjha.shared.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FIX (TD4/S1, architecture-review.md §2.2): this listener previously logged
 * the raw OTP and the user's email to stdout via {@code System.out.println}
 * — a live secrets leak, not a style issue — and ran on a plain
 * {@code @EventListener}, meaning it could fire on a transaction that later
 * rolled back.
 *
 * Two changes, not one:
 *  1. {@code @TransactionalEventListener} (default phase AFTER_COMMIT):
 *     never send an OTP email for a code that was generated in a transaction
 *     that didn't actually commit.
 *  2. {@code @Async}, kept alongside it (not dropped): AFTER_COMMIT
 *     registration happens synchronously during commit, then execution is
 *     handed to the async executor — this closes the correctness gap
 *     (TD4/TD1's core defect) while *also* fixing the secondary issue
 *     architecture-review.md §2.2 flagged in the same breath: every
 *     OTP-triggering call (register, resend, password reset) was latency-
 *     bound to SMTP because this ran synchronously in the request thread.
 *     Commit-safety and non-blocking dispatch are complementary, not
 *     alternatives — there was no reason to pick only one.
 *
 * The println is simply deleted, not replaced with a log statement that
 * still contains the raw OTP. If OTP delivery ever needs to be debugged,
 * log the event type and a truncated/hashed identifier, never the code itself.
 */
@Component
@RequiredArgsConstructor
public class AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEventListener.class);

    private final EmailService emailService;

    @Async
    @TransactionalEventListener
    public void handleOtpGenerated(OtpGeneratedEvent event) {
        log.debug("Dispatching OTP email for purpose={} (recipient omitted from logs by design)", event.purpose());
        emailService.sendOtpEmail(event.email(), event.rawOtp(), event.purpose());
    }
}
