package com.family.expensemanager.auth.security.oauth2;

import java.util.Map;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.family.expensemanager.auth.domain.entity.User;

/** Wraps a non-OIDC provider's (Facebook) attributes together with our resolved local {@link User}. */
public class AppOAuth2User extends DefaultOAuth2User implements AppUserPrincipal {

    private final User user;

    public AppOAuth2User(User user, Map<String, Object> attributes, String nameAttributeKey) {
        super(AuthorityUtils.NO_AUTHORITIES, attributes, nameAttributeKey);
        this.user = user;
    }

    @Override
    public User getUser() {
        return user;
    }
}
