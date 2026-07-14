package com.saanjha.modules.chat.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the multi-session presence bug found during the
 * chat-websocket-auth hardening sprint: a user with more than one live STOMP
 * session (multiple tabs/devices) must stay ONLINE until the LAST session
 * disconnects, not the first. Runs against a real Redis instance (not a
 * mock) because the correctness here hinges on Redis's atomic INCR/DECR
 * semantics under concurrent access, which a mock cannot meaningfully
 * verify.
 */
@Testcontainers
class PresenceServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    private PresenceService presenceService;

    @BeforeAll
    static void startRedis() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
        presenceService = new PresenceService(redisTemplate);

        // Isolate each test: flush so leftover keys from a previous test never leak in.
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @Test
    void singleSession_connectThenDisconnect_goesOnlineThenOffline() {
        UUID userId = UUID.randomUUID();

        presenceService.sessionConnected(userId);
        assertThat(presenceService.getStatus(userId)).isEqualTo("ONLINE");

        presenceService.sessionDisconnected(userId);
        assertThat(presenceService.getStatus(userId)).isEqualTo("OFFLINE");
    }

    @Test
    void twoSessionsSameUser_disconnectingOneSession_staysOnline() {
        UUID userId = UUID.randomUUID();

        presenceService.sessionConnected(userId); // tab 1 (e.g. laptop)
        presenceService.sessionConnected(userId); // tab 2 (e.g. phone)
        assertThat(presenceService.getStatus(userId)).isEqualTo("ONLINE");

        presenceService.sessionDisconnected(userId); // laptop tab closes
        assertThat(presenceService.getStatus(userId))
                .as("user still has one live session (phone) and must remain ONLINE")
                .isEqualTo("ONLINE");

        presenceService.sessionDisconnected(userId); // phone disconnects too
        assertThat(presenceService.getStatus(userId))
                .as("last live session gone - now OFFLINE")
                .isEqualTo("OFFLINE");
    }

    @Test
    void heartbeat_refreshesWithoutChangingStatus() {
        UUID userId = UUID.randomUUID();
        presenceService.sessionConnected(userId);
        presenceService.setStatus(userId, "AWAY");

        presenceService.heartbeat(userId);

        assertThat(presenceService.getStatus(userId))
                .as("heartbeat must not overwrite an explicitly-set status")
                .isEqualTo("AWAY");
    }

    @Test
    void heartbeat_withNoTrackedSession_selfHealsToOnline() {
        UUID userId = UUID.randomUUID();
        // No sessionConnected ever called (e.g. the session-count key expired
        // because a DISCONNECT frame was lost on a network drop) - a heartbeat
        // arriving anyway should re-establish exactly one live session.
        presenceService.heartbeat(userId);

        assertThat(presenceService.getStatus(userId)).isEqualTo("ONLINE");

        presenceService.sessionDisconnected(userId);
        assertThat(presenceService.getStatus(userId)).isEqualTo("OFFLINE");
    }

    @Test
    void setStatus_whenNoLiveSession_doesNotResurrectPresence() {
        UUID userId = UUID.randomUUID();
        // Never connected - an explicit status update for a disconnected user
        // (e.g. a stray/duplicate message) must not fabricate an ONLINE state.
        presenceService.setStatus(userId, "ONLINE");

        assertThat(presenceService.getStatus(userId)).isEqualTo("OFFLINE");
    }

    @Test
    void unrelatedUsersPresence_isIndependent() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        presenceService.sessionConnected(userA);
        assertThat(presenceService.getStatus(userA)).isEqualTo("ONLINE");
        assertThat(presenceService.getStatus(userB)).isEqualTo("OFFLINE");

        presenceService.sessionDisconnected(userA);
        assertThat(presenceService.getStatus(userA)).isEqualTo("OFFLINE");
        assertThat(presenceService.getStatus(userB)).isEqualTo("OFFLINE");
    }

    /**
     * Simulates N concurrent tabs/devices for the same user connecting and
     * disconnecting in an overlapping, non-deterministic order (e.g. several
     * browser tabs opened at once, then closed in a random order) - the
     * session count must never go negative or leave the user stuck ONLINE
     * with zero live sessions.
     */
    @Test
    void concurrentConnectsAndDisconnects_forSameUser_neverLeaveInconsistentState() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        int sessions = 8;
        ExecutorService pool = Executors.newFixedThreadPool(sessions);
        AtomicInteger errors = new AtomicInteger();
        try {
            CountDownLatch connectLatch = new CountDownLatch(sessions);
            for (int i = 0; i < sessions; i++) {
                pool.submit(() -> {
                    try {
                        presenceService.sessionConnected(userId);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        connectLatch.countDown();
                    }
                });
            }
            assertThat(connectLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(errors.get()).isZero();
            assertThat(presenceService.getStatus(userId)).isEqualTo("ONLINE");

            CountDownLatch disconnectLatch = new CountDownLatch(sessions);
            for (int i = 0; i < sessions; i++) {
                pool.submit(() -> {
                    try {
                        presenceService.sessionDisconnected(userId);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        disconnectLatch.countDown();
                    }
                });
            }
            assertThat(disconnectLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(errors.get()).isZero();
            assertThat(presenceService.getStatus(userId))
                    .as("all sessions disconnected - must end up OFFLINE, not stuck ONLINE")
                    .isEqualTo("OFFLINE");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
