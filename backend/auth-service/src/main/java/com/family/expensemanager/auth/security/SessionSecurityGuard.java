package com.family.expensemanager.auth.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.auth.dao.RefreshTokenDao;

import lombok.RequiredArgsConstructor;

/**
 * A separate bean (not just a method on {@link com.family.expensemanager.auth.service.AuthService})
 * purely so {@link #revokeAllSessionsImmediately} can run in its own, always-committed transaction:
 * calling it from {@code AuthService#refresh} happens right before that method throws — its enclosing
 * transaction (class-level {@code @Transactional} on AuthService) gets rolled back, but the security
 * response to a stolen/replayed refresh token — killing every session — must survive that rollback.
 * {@code REQUIRES_NEW} only takes effect on a call that goes through the Spring proxy, so this can't be
 * a private method AuthService calls on itself.
 */
@Component
@RequiredArgsConstructor
public class SessionSecurityGuard {

    private final RefreshTokenDao refreshTokenDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessionsImmediately(Long userId) {
        refreshTokenDao.revokeAllByUserId(userId);
    }
}
