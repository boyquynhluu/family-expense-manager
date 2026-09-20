package com.family.expensemanager.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.User;

@ExtendWith(MockitoExtension.class)
class TotpKeyRotationTest {

    private static final String OLD_KEY = "ZGV2LW9ubHktdG90cC1lbmNyeXB0aW9uLWtleS0zMmI=";
    private static final String NEW_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock
    private UserDao userDao;

    @Test
    void run_reencryptsOnlySecretsNotOnCurrentKey() {
        TotpSecretCipher oldCipher = new TotpSecretCipher(OLD_KEY);
        TotpSecretCipher rotated = new TotpSecretCipher(NEW_KEY, OLD_KEY);

        User onOldKey = user(1L, oldCipher.encrypt("SECRETOLD"));
        User onNewKey = user(2L, rotated.encrypt("SECRETNEW"));
        User plaintext = user(3L, "SECRETPLAIN");
        when(userDao.selectAllWithTotpSecret()).thenReturn(List.of(onOldKey, onNewKey, plaintext));

        new TotpKeyRotation(userDao, rotated).run(null);

        verify(userDao).update(onOldKey);
        verify(userDao).update(plaintext);
        verify(userDao, never()).update(onNewKey);
        assertThat(rotated.decrypt(onOldKey.getTotpSecret())).isEqualTo("SECRETOLD");
        assertThat(new TotpSecretCipher(NEW_KEY).decrypt(onOldKey.getTotpSecret())).isEqualTo("SECRETOLD");
        assertThat(new TotpSecretCipher(NEW_KEY).decrypt(plaintext.getTotpSecret())).isEqualTo("SECRETPLAIN");
    }

    @Test
    void run_keepsGoing_whenOneSecretCannotBeDecrypted() {
        TotpSecretCipher rotatedWithoutPrevious = new TotpSecretCipher(NEW_KEY);
        User unreadable = user(1L, new TotpSecretCipher(OLD_KEY).encrypt("SECRETOLD"));
        User plaintext = user(2L, "SECRETPLAIN");
        when(userDao.selectAllWithTotpSecret()).thenReturn(List.of(unreadable, plaintext));

        new TotpKeyRotation(userDao, rotatedWithoutPrevious).run(null);

        verify(userDao, never()).update(unreadable);
        verify(userDao).update(plaintext);
    }

    private static User user(Long id, String totpSecret) {
        User user = new User();
        user.setId(id);
        user.setTotpSecret(totpSecret);
        return user;
    }
}
