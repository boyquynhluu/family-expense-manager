package com.family.expensemanager.auth.security.oauth2;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Handles Google (an OpenID Connect provider) sign-in: {@code registrationId} = "google". */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "AppOidcUserService")
public class AppOidcUserService extends OidcUserService {

    private final AuthService authService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("loadUser - start");
        OidcUser oidcUser = super.loadUser(userRequest);
        // processOAuth2User trusts the email to link/activate accounts, so it must be one Google actually verified.
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            log.warn("loadUser - email Google chưa được xác thực, subject={}", oidcUser.getSubject());
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"),
                    "Email Google chưa được xác thực");
        }

        String displayName = oidcUser.getFullName() != null ? oidcUser.getFullName() : oidcUser.getEmail();
        User user = authService.processOAuth2User("GOOGLE", oidcUser.getSubject(), oidcUser.getEmail(), displayName);

        return new AppOidcUser(user, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
