package com.family.expensemanager.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Kept separate from {@link SecurityConfig} so creating the {@link PasswordEncoder} bean
 * doesn't require instantiating SecurityConfig first. SecurityConfig depends (via
 * AppOidcUserService/AppOAuth2UserService) on AuthService, which itself needs a
 * PasswordEncoder — declaring it inside SecurityConfig created a circular dependency
 * (authService -> securityConfig -> appOidcUserService -> authService).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
