package com.family.expensemanager.auth.security.oauth2;

import com.family.expensemanager.auth.domain.entity.User;

/** Implemented by the OAuth2/OIDC principal wrappers so the success handler can reach our own {@link User} row. */
public interface AppUserPrincipal {

    User getUser();
}
