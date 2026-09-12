package com.family.expensemanager.auth.security.oauth2;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final String successRedirectUrl;

    public OAuth2AuthenticationFailureHandler(@Value("${app.oauth2.success-redirect-url}") String successRedirectUrl) {
        this.successRedirectUrl = successRedirectUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String targetUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam("error", "oauth2_login_failed")
                .build()
                .toUriString();
        response.sendRedirect(targetUrl);
    }
}
