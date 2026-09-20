package com.family.expensemanager.auth.security;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * On startup re-encrypts every stored TOTP secret that is still legacy plaintext or encrypted with
 * the previous key, so TOTP_ENCRYPTION_KEY_PREVIOUS can be removed once this has run.
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "TotpKeyRotation")
public class TotpKeyRotation implements ApplicationRunner {

    private final UserDao userDao;
    private final TotpSecretCipher totpSecretCipher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> users = userDao.selectAllWithTotpSecret();
        int rotated = 0;
        for (User user : users) {
            try {
                String reencrypted = totpSecretCipher.reencryptIfNeeded(user.getTotpSecret());
                if (reencrypted != null) {
                    user.setTotpSecret(reencrypted);
                    userDao.update(user);
                    rotated++;
                }
            } catch (IllegalStateException e) {
                log.error("Không chuyển được TOTP secret của userId={}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("TotpKeyRotation - kiểm tra {} secret, đã mã hoá lại {}", users.size(), rotated);
    }
}
