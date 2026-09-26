package com.family.expensemanager.auth.security;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.family.expensemanager.auth.dao.RefreshTokenDao;

@ExtendWith(MockitoExtension.class)
class SessionSecurityGuardTest {

    @Mock
    private RefreshTokenDao refreshTokenDao;

    @Test
    void revokeAllSessionsImmediately_delegatesToRefreshTokenDao() {
        new SessionSecurityGuard(refreshTokenDao).revokeAllSessionsImmediately(7L);

        verify(refreshTokenDao).revokeAllByUserId(7L);
    }
}
