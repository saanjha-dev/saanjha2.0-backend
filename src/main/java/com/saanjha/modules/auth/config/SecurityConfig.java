package com.saanjha.modules.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

// ... other imports
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Inject configuration values seamlessly
    @Value("${app.security.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${app.security.allow-credentials:true}")
    private boolean allowCredentials;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v1/auth/register",
                                "/v1/auth/login",
                                "/v1/auth/refresh",
                                "/v1/auth/verify-email",
                                "/v1/auth/resend-verification",
                                "/v1/auth/forgot-password",
                                "/v1/auth/reset-password",
                                "/v1/auth/verify-reset-otp"
                        ).permitAll()
                        // FIX (TD5/S2, architecture-review.md §3): this used to be a blanket
                        // "/v1/users/**" permitAll() covering all 13 endpoints under that
                        // prefix, 11 of which require authentication and were protected only
                        // by a service-layer convention (SecurityUtils.getCurrentUserId()
                        // throwing), not by the filter chain itself — meaning every future
                        // endpoint added under this prefix silently inherited an insecure
                        // default unless someone remembered to lock it down individually.
                        // Scoped now to exactly the two routes UserController actually
                        // intends to be public, both GET, both read-only.
                        // FIX (TD5/S2, architecture-review.md §3): this used to be a blanket
                        // "/v1/users/**" permitAll() covering all 13 endpoints under that
                        // prefix, 11 of which require authentication and were protected only
                        // by a service-layer convention (SecurityUtils.getCurrentUserId()
                        // throwing), not by the filter chain itself — meaning every future
                        // endpoint added under this prefix silently inherited an insecure
                        // default unless someone remembered to lock it down individually.
                        // Scoped now to exactly the two routes UserController actually
                        // intends to be public, both GET, both read-only.
                        //
                        // ORDERING NOTE: Spring Security evaluates these rules in
                        // declaration order and stops at the first match. "/v1/users/me"
                        // and "/v1/users/{userId}" are the same single-path-segment shape
                        // (GET /v1/users/me literally matches the {userId} pattern too), so
                        // the authenticated-only "/me" rule below MUST be declared before
                        // the permitAll "{userId}" pattern, or "me" would incorrectly become
                        // publicly readable through the general pattern.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/users/me").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/users/{userId}").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/users/handle/**").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        // Public read access to project listings/detail (Security Matrix: GUEST can "View public projects").
                        // Visibility rules for DRAFT/ARCHIVED are still enforced in ProjectService, not here.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/projects/**").permitAll()
                        // Portfolio: the Verifier/Recruiter persona (MES §0.3) is explicitly meant to browse
                        // anonymously. Same ordering hazard as "/v1/users/me" above — "/v1/portfolios/me/**"
                        // is a single-segment shape that the general "/{userId}" pattern would also match, so
                        // the authenticated "me" rules MUST be declared first. PortfolioService itself still
                        // enforces PRIVATE/LINK_ONLY visibility for the permitAll routes below — this filter
                        // chain only controls who reaches the controller at all, not what they're shown.
                        .requestMatchers("/v1/portfolios/me/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/portfolios/{userId}").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/portfolios/shared/**").permitAll()
                        // Notification: NotificationHub (the external provider) calls this to report
                        // delivery-status callbacks - there is no JWT to present, so this can't be
                        // "authenticated()". ProviderWebhookController verifies the HMAC signature on
                        // every request itself before touching any data - permitAll here only means
                        // "reaches the controller", not "trusted without verification" (same pattern
                        // as every other permitAll route in this file leaving further checks to the
                        // service layer, e.g. Portfolio's visibility gate above).
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/v1/notifications/webhooks/**").permitAll()
                        // Discovery: search/autocomplete/trending are the platform's public listing surface
                        // (MES §0.3 — Builder/Lead/Recruiter personas all need to browse without an account),
                        // same reasoning as Project's and Portfolio's public GET routes above. Personalized
                        // routes ("/v1/discovery/me/**", recommendations, matching) are deliberately NOT listed
                        // here, so they fall through to the default authenticated() rule below.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/discovery/search/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/discovery/suggestions").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/v1/discovery/trending/**").permitAll()
                        // Chat: the STOMP handshake itself carries no JWT the filter chain can see
                        // (browsers can't set an Authorization header on the WebSocket upgrade
                        // request) - real authentication happens one layer up, at the STOMP
                        // CONNECT frame, via ChatChannelInterceptor#preSend, which rejects any
                        // CONNECT frame without a valid Bearer token before a Principal is ever
                        // attached to the session. permitAll here only means "the HTTP upgrade
                        // reaches the WebSocket layer", not "unauthenticated STOMP traffic is
                        // accepted" - same permitAll-then-service-layer-enforces pattern as every
                        // other permitAll route in this file.
                        .requestMatchers("/ws/chat/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Master Engineered: Powered entirely by the configuration layer
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}