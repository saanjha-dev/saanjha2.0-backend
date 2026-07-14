package com.saanjha.modules.chat.websocket;

import com.saanjha.modules.auth.entity.AuthUser;
import com.saanjha.modules.auth.repository.AuthUserRepository;
import com.saanjha.modules.auth.service.JwtProvider;
import com.saanjha.modules.chat.service.PresenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof (real embedded server, real Postgres, real Redis, a real
 * {@link WebSocketStompClient}) that the chat WebSocket auth lifecycle holds
 * up across the scenarios the hardening-sprint brief called out explicitly:
 * valid/invalid CONNECT, multiple concurrent sessions for one user
 * (tabs/devices), and reconnects. {@link ChatChannelInterceptorTest} and
 * {@link com.saanjha.modules.chat.service.PresenceServiceTest} already prove
 * the unit-level logic in isolation with mocks/a bare Redis client - this
 * class proves the same guarantees hold when wired together through the
 * actual Spring container and a real wire protocol.
 *
 * INFRASTRUCTURE NOTE: this boots the full application context (every
 * module), because the STOMP endpoint is registered by the same
 * {@code WebSocketConfig} that lives inside that context - there's no
 * lighter-weight Spring Boot test slice for "just the WebSocket layer" the
 * way {@code @WebMvcTest} exists for MVC. Postgres and Redis run as real
 * Testcontainers; RabbitMQ, mail, Cloudinary, and the Notification Hub SDK
 * are given inert dummy configuration since nothing in this test path
 * exercises them and none of those beans open a connection at construction
 * time (verified by reading their auto-configuration/SDK code paths - see
 * inline comments below on the one that does validate eagerly).
 *
 * REQUIRES DOCKER: like every other Testcontainers-based test in this
 * project (e.g. {@code ChatRepositoryTest}), this needs a Docker daemon
 * reachable from the machine running the suite.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // App-specific settings this test path actually depends on:
        "app.security.allowed-origins=*",
        // Inert placeholders for subsystems this test never exercises. None of
        // these open a network connection at bean-construction time in this
        // codebase's current autoconfiguration - only NotificationHubClient.Builder
        // validates eagerly (throws IllegalArgumentException on blank key/secret,
        // per the comment in application.yml), so it gets non-blank dummy values.
        "spring.mail.username=test@example.com",
        "spring.mail.password=dummy",
        "app.cloudinary.cloud-name=dummy",
        "app.cloudinary.api-key=dummy",
        "app.cloudinary.api-secret=dummy",
        "notification.hub.api-key=dummy-key",
        "notification.hub.api-secret=dummy-secret",
        "notification.webhook.signing-secret=dummy-signing-secret"
})
class ChatWebSocketIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PresenceService presenceService;

    private final java.util.List<WebSocketStompClient> stompClients = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        stompClients.forEach(WebSocketStompClient::stop);
        stompClients.clear();
    }

    @Test
    void connect_withValidToken_establishesStompSession() throws Exception {
        String token = registerUserAndGetToken("valid-connect@example.com");

        StompSession session = connect(token).get(10, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void connect_withInvalidToken_neverEstablishesASession() {
        CompletableFuture<StompSession> future = connect("this-is-not-a-real-jwt");

        // The server rejects CONNECT inside ChatChannelInterceptor and closes
        // the transport; depending on client-library timing that surfaces
        // either as an exceptional future or a timeout waiting on one that
        // never completes. Either outcome proves the same thing: no working,
        // authenticated STOMP session ever comes into existence for a bad token.
        boolean neverConnected;
        try {
            StompSession session = future.get(5, TimeUnit.SECONDS);
            neverConnected = !session.isConnected();
        } catch (ExecutionException | TimeoutException | InterruptedException expected) {
            neverConnected = true;
        }
        assertThat(neverConnected).isTrue();
    }

    @Test
    void connect_withNoAuthorizationHeaderAtAll_neverEstablishesASession() {
        WebSocketStompClient client = newStompClient();
        stompClients.add(client);
        CompletableFuture<StompSession> future = client.connectAsync(
                wsUrl(), new WebSocketHttpHeaders(), new StompHeaders(), new StompSessionHandlerAdapter() {});

        boolean neverConnected;
        try {
            StompSession session = future.get(5, TimeUnit.SECONDS);
            neverConnected = !session.isConnected();
        } catch (ExecutionException | TimeoutException | InterruptedException expected) {
            neverConnected = true;
        }
        assertThat(neverConnected).isTrue();
    }

    @Test
    void twoConcurrentSessionsForSameUser_disconnectingOne_leavesUserOnline() throws Exception {
        String token = registerUserAndGetToken("multi-tab@example.com");
        UUID userId = jwtProvider.validateAndGetUserId(token);

        StompSession tabA = connect(token).get(10, TimeUnit.SECONDS);
        StompSession tabB = connect(token).get(10, TimeUnit.SECONDS);

        waitUntilPresenceIs(userId, "ONLINE");

        tabA.disconnect();

        waitUntilPresenceIs(userId, "ONLINE", "second tab is still connected - user must remain ONLINE");

        tabB.disconnect();

        waitUntilPresenceIs(userId, "OFFLINE");
    }

    @Test
    void reconnect_afterFullDisconnect_goesOfflineThenOnlineAgain() throws Exception {
        String token = registerUserAndGetToken("reconnect@example.com");
        UUID userId = jwtProvider.validateAndGetUserId(token);

        StompSession first = connect(token).get(10, TimeUnit.SECONDS);
        waitUntilPresenceIs(userId, "ONLINE");

        first.disconnect();
        waitUntilPresenceIs(userId, "OFFLINE");

        StompSession second = connect(token).get(10, TimeUnit.SECONDS);
        waitUntilPresenceIs(userId, "ONLINE");

        second.disconnect();
        waitUntilPresenceIs(userId, "OFFLINE");
    }

    /**
     * Presence changes happen asynchronously relative to the STOMP
     * CONNECT/DISCONNECT frame being sent over the wire (the interceptor runs
     * on the inbound channel's own thread, off the test thread), so
     * assertions poll briefly rather than racing a single synchronous check.
     * No Awaitility dependency exists in this project's pom yet, so this is a
     * small hand-rolled poll rather than pulling in a new test framework for
     * one file.
     */
    private void waitUntilPresenceIs(UUID userId, String expectedStatus) throws InterruptedException {
        waitUntilPresenceIs(userId, expectedStatus, null);
    }

    private void waitUntilPresenceIs(UUID userId, String expectedStatus, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = presenceService.getStatus(userId);
            if (expectedStatus.equals(last)) {
                return;
            }
            Thread.sleep(100);
        }
        var assertion = assertThat(last);
        if (description != null) {
            assertion = assertion.as(description);
        }
        assertion.isEqualTo(expectedStatus);
    }

    private String registerUserAndGetToken(String email) {
        AuthUser user = new AuthUser();
        user.setEmail(email);
        user.setPasswordHash("{bcrypt}$2a$10$dummydummydummydummydummydummydummydummydummydumm");
        user.setEmailVerified(true);
        user.setStatus(AuthUser.AccountStatus.ACTIVE);
        AuthUser saved = authUserRepository.save(user);
        return jwtProvider.generateAccessToken(saved.getId(), saved.getEmail());
    }

    private CompletableFuture<StompSession> connect(String bearerToken) {
        WebSocketStompClient client = newStompClient();
        stompClients.add(client);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + bearerToken);
        return client.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {});
    }

    private WebSocketStompClient newStompClient() {
        return new WebSocketStompClient(new StandardWebSocketClient());
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws/chat";
    }
}
