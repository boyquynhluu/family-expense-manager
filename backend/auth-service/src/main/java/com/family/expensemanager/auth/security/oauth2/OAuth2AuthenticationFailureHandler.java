package com.family.expensemanager.auth.security.oauth2;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.family.expensemanager.auth.service.AuthService;

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
        String errorCode = exception instanceof OAuth2AuthenticationException oauth2Exception
                && AuthService.OAUTH2_ACCOUNT_LOCKED_ERROR.equals(oauth2Exception.getError().getErrorCode())
                ? AuthService.OAUTH2_ACCOUNT_LOCKED_ERROR
                : "oauth2_login_failed";
        String targetUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam("error", errorCode)
                .build()
                .toUriString();
        response.sendRedirect(targetUrl);
    }
}
