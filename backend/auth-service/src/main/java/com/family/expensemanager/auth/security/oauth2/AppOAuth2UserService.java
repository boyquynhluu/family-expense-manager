package com.family.expensemanager.auth.security.oauth2;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Handles non-OIDC providers (Facebook) sign-in: {@code registrationId} = "facebook". */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "AppOAuth2UserService")
public class AppOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthService authService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("loadUser - start, registrationId={}", userRequest.getClientRegistration().getRegistrationId());
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String providerId = String.valueOf(attributes.get("id"));
        String email = (String) attributes.get("email");
        String displayName = (String) attributes.getOrDefault("name", email);

        User user = authService.processOAuth2User(registrationId.toUpperCase(), providerId, email, displayName);

        return new AppOAuth2User(user, attributes, "id");
    }
}
