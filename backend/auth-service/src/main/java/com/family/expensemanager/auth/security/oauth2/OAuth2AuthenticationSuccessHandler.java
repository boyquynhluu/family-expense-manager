package com.family.expensemanager.auth.security.oauth2;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.family.expensemanager.auth.dto.AuthResponse;
import com.family.expensemanager.auth.security.RequestMetadataUtil;
import com.family.expensemanager.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Issues our own access/refresh token pair for the freshly authenticated OAuth2 user
 * (same {@link AuthService#issueTokens} used by password login) and redirects to the
 * frontend's callback page with both tokens as query params. The frontend (see
 * AuthContext.jsx) already keeps both tokens in localStorage and refreshes with a
 * POST body rather than a cookie, so the OAuth2 path hands them over the same way
 * password login does instead of introducing a second, cookie-based mechanism.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final String successRedirectUrl;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            @Value("${app.oauth2.success-redirect-url}") String successRedirectUrl) {
        this.authService = authService;
        this.successRedirectUrl = successRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof AppUserPrincipal appUserPrincipal)) {
            throw new IllegalStateException(
                    "Unexpected OAuth2 principal type: " + authentication.getPrincipal().getClass());
        }

        if (Boolean.TRUE.equals(appUserPrincipal.getUser().getTotpEnabled())) {
            // A Google/Facebook login proves the email, not the 2FA code — hand the frontend a
            // challenge (same one password login issues) instead of tokens.
            String challengeToken = authService.issueTwoFactorChallenge(appUserPrincipal.getUser().getId());
            response.sendRedirect(UriComponentsBuilder.fromUriString(successRedirectUrl)
                    .queryParam("twoFactorToken", challengeToken)
                    .build()
                    .toUriString());
            return;
        }

        AuthResponse tokens = authService.issueTokens(appUserPrincipal.getUser(),
                RequestMetadataUtil.deviceInfo(request), RequestMetadataUtil.ipAddress(request));

        String targetUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam("accessToken", tokens.accessToken())
                .queryParam("refreshToken", tokens.refreshToken())
                .build()
                .toUriString();
        response.sendRedirect(targetUrl);
    }
}
