package com.family.expensemanager.auth.security.oauth2;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import com.family.expensemanager.auth.domain.entity.User;

/** Wraps Google's OIDC claims together with our resolved local {@link User}. */
public class AppOidcUser extends DefaultOidcUser implements AppUserPrincipal {

    private final User user;

    public AppOidcUser(User user, OidcIdToken idToken, OidcUserInfo userInfo) {
        super(AuthorityUtils.NO_AUTHORITIES, idToken, userInfo);
        this.user = user;
    }

    @Override
    public User getUser() {
        return user;
    }
}
