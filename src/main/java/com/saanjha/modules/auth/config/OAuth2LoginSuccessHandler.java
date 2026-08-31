package com.saanjha.modules.auth.config;

import com.saanjha.modules.auth.dto.ResponseDTOs.AuthTokens;
import com.saanjha.modules.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = token.getPrincipal();

        String email = oauth2User.getAttribute("email");
        if (email == null) {
            // Some providers might not provide an email, though Google usually does. 
            // GitHub requires the `user:email` scope. 
            // Fallback to login name for GitHub if email is missing but usually it shouldn't be.
            email = oauth2User.getAttribute("login") + "@github.com";
        }
        
        String providerId = oauth2User.getName(); // Usually the sub or id
        String authProvider = token.getAuthorizedClientRegistrationId().toUpperCase();
        
        String clientIp = request.getRemoteAddr();

        // Perform login or registration via AuthService
        AuthTokens tokens = authService.oauthLogin(email, authProvider, providerId, clientIp);

        // Redirect to the frontend callback URL with the tokens
        String frontendCallbackUrl = "http://localhost:3000/login/oauth-callback";
        String redirectUrl = String.format("%s?accessToken=%s&refreshToken=%s", 
            frontendCallbackUrl, 
            tokens.accessToken(), 
            tokens.refreshToken() != null ? tokens.refreshToken() : "");
            
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
