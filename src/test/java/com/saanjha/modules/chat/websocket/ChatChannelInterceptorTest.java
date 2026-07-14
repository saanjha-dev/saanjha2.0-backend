package com.saanjha.modules.chat.websocket;

import com.saanjha.modules.auth.service.JwtProvider;
import com.saanjha.modules.chat.service.PresenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the P0 chat-websocket-auth hardening:
 *  - CONNECT accept/reject paths
 *  - DISCONNECT presence propagation
 *  - Regression guard: SecurityContextHolder must NEVER be written to by this
 *    interceptor (see class javadoc / hardening-sprint commit) - it is
 *    ThreadLocal-backed and the clientInboundChannel dispatches frames on a
 *    shared, reused thread pool, so a write here could leak one user's
 *    identity into another user's frame processed on the same thread later.
 *  - A concurrency test proving that two different users' CONNECT frames,
 *    processed on two different pooled threads, never cross-contaminate -
 *    the exact scenario the removed SecurityContextHolder call would have
 *    been vulnerable to.
 */
class ChatChannelInterceptorTest {

    private JwtProvider jwtProvider;
    private PresenceService presenceService;
    private ChatChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtProvider.class);
        presenceService = mock(PresenceService.class);
        interceptor = new ChatChannelInterceptor(jwtProvider, presenceService);
        // Start every test from a known-clean thread-local state.
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void connect_withValidBearerToken_authenticatesSessionAndMarksSessionConnected() {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.validateAndGetUserId("good-token")).thenReturn(userId);

        Message<?> message = connectMessage("Bearer good-token");
        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Principal user = resultAccessor.getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo(userId.toString());

        verify(presenceService).sessionConnected(userId);
    }

    @Test
    void connect_neverWritesToSecurityContextHolder() {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.validateAndGetUserId("good-token")).thenReturn(userId);

        interceptor.preSend(connectMessage("Bearer good-token"), null);

        // Regression guard for the fixed vulnerability: only accessor.setUser()
        // may carry identity across the STOMP pipeline; SecurityContextHolder
        // must remain untouched on the processing thread.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void connect_withMissingAuthorizationHeader_rejectsAndNeverTouchesPresence() {
        Message<?> message = connectMessageWithoutAuthHeader();

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);

        verifyNoInteractions(presenceService);
    }

    @Test
    void connect_withMalformedAuthorizationHeader_rejects() {
        Message<?> message = connectMessage("Basic not-a-bearer-token");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);

        verifyNoInteractions(presenceService);
    }

    @Test
    void connect_withInvalidOrExpiredToken_rejectsAndNeverMarksConnected() {
        when(jwtProvider.validateAndGetUserId("bad-token"))
                .thenThrow(new RuntimeException("token expired"));

        Message<?> message = connectMessage("Bearer bad-token");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);

        verify(presenceService, never()).sessionConnected(any());
    }

    @Test
    void disconnect_withAuthenticatedUser_marksSessionDisconnected() {
        UUID userId = UUID.randomUUID();
        Message<?> message = disconnectMessage(userId);

        interceptor.preSend(message, null);

        verify(presenceService).sessionDisconnected(userId);
    }

    @Test
    void disconnect_withoutAnAuthenticatedUser_isNoOp() {
        // e.g. a raw disconnect on a session whose CONNECT was rejected/never completed.
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, null);

        verifyNoInteractions(presenceService);
    }

    @Test
    void nonConnectNonDisconnectFrames_passThroughWithoutTouchingPresence() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/conversations/" + UUID.randomUUID() + "/send");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(jwtProvider);
        verifyNoInteractions(presenceService);
    }

    /**
     * Reproduces the exact failure mode of the removed
     * SecurityContextHolder.setAuthentication(...) call: two different
     * users' CONNECT frames are processed on a small, shared, reused thread
     * pool (mirroring Spring's clientInboundChannel executor). With the fix
     * in place, nothing is thread-local, so there is nothing to leak -
     * asserted here by checking SecurityContextHolder stays empty on every
     * worker thread throughout, while each frame's own accessor.getUser()
     * still resolves to the correct, distinct user.
     */
    @Test
    void concurrentConnectsForDifferentUsers_onSharedThreadPool_doNotCrossContaminate() throws InterruptedException {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            when(jwtProvider.validateAndGetUserId("token-a")).thenReturn(userA);
            when(jwtProvider.validateAndGetUserId("token-b")).thenReturn(userB);

            CountDownLatch bothArrived = new CountDownLatch(threads);
            AtomicReference<String> resolvedA = new AtomicReference<>();
            AtomicReference<String> resolvedB = new AtomicReference<>();
            AtomicReference<Authentication> leakedIntoA = new AtomicReference<>();
            AtomicReference<Authentication> leakedIntoB = new AtomicReference<>();

            Runnable taskA = () -> {
                bothArrived.countDown();
                awaitQuietly(bothArrived);
                Message<?> result = interceptor.preSend(connectMessage("Bearer token-a"), null);
                resolvedA.set(StompHeaderAccessor.wrap(result).getUser().getName());
                leakedIntoA.set(SecurityContextHolder.getContext().getAuthentication());
            };
            Runnable taskB = () -> {
                bothArrived.countDown();
                awaitQuietly(bothArrived);
                Message<?> result = interceptor.preSend(connectMessage("Bearer token-b"), null);
                resolvedB.set(StompHeaderAccessor.wrap(result).getUser().getName());
                leakedIntoB.set(SecurityContextHolder.getContext().getAuthentication());
            };

            // Submit repeatedly across the fixed 2-thread pool so threads are
            // reused across "sessions" (users), same as production.
            for (int round = 0; round < 25; round++) {
                pool.submit(taskA).get(2, TimeUnit.SECONDS);
                pool.submit(taskB).get(2, TimeUnit.SECONDS);

                assertThat(resolvedA.get()).isEqualTo(userA.toString());
                assertThat(resolvedB.get()).isEqualTo(userB.toString());
                assertThat(leakedIntoA.get()).as("SecurityContextHolder must stay empty on the worker thread").isNull();
                assertThat(leakedIntoB.get()).as("SecurityContextHolder must stay empty on the worker thread").isNull();
            }
        } catch (Exception e) {
            throw new AssertionError("Concurrent CONNECT handling failed", e);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Message<?> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", authorizationHeader);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> connectMessageWithoutAuthHeader() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> disconnectMessage(UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setUser(() -> userId.toString());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
