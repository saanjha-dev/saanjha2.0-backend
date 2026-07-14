package com.saanjha.shared.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the fix for the gap found during the P0-2 audit: before this class
 * existed, an anonymous request to a protected route got Spring Security's
 * bare default 403 with no body at all - wrong status (should be 401, no
 * credentials were presented at all) and no ApiEnvelope. This test exercises
 * the entry point directly against a real (not mocked) Jackson ObjectMapper
 * and a real MockHttpServletResponse, so it's actually asserting on bytes
 * written to the response, not on which methods got called.
 */
class RestAuthenticationEntryPointTest {

    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(new ObjectMapper());

    @Test
    void commence_writes401WithApiEnvelopeShapedBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/portfolios/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("no auth present"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("data").isMissingNode() || body.path("data").isNull()).isTrue();
    }
}
