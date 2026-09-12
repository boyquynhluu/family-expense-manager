package com.family.expensemanager.auth.security.oauth2;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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

        String displayName = oidcUser.getFullName() != null ? oidcUser.getFullName() : oidcUser.getEmail();
        User user = authService.processOAuth2User("GOOGLE", oidcUser.getSubject(), oidcUser.getEmail(), displayName);

        return new AppOidcUser(user, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
