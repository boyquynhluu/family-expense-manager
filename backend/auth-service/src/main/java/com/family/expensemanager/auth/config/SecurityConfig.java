package com.family.expensemanager.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.family.expensemanager.auth.security.oauth2.AppOAuth2UserService;
import com.family.expensemanager.auth.security.oauth2.AppOidcUserService;
import com.family.expensemanager.auth.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.family.expensemanager.auth.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.family.expensemanager.auth.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.family.expensemanager.common.security.JwtAuthenticationFilter;
import com.family.expensemanager.common.security.JwtUtil;
import com.family.expensemanager.common.security.RevokedSessionStore;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final RevokedSessionStore revokedSessionStore;
    private final AppOidcUserService appOidcUserService;
    private final AppOAuth2UserService appOAuth2UserService;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/verify", "/api/auth/verify-email-change",
                                "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/2fa/verify-login").permitAll()
                        .requestMatchers("/api/auth/oauth2/**", "/api/auth/login/oauth2/**").permitAll()
                        // Viewing/accepting a family invite needs no prior login — only sending one
                        // (POST /api/auth/invite, no path segment after it) requires auth.
                        .requestMatchers(HttpMethod.GET, "/api/auth/invite/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/invite/*/accept").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(a -> a
                                .baseUri("/api/auth/oauth2/authorization")
                                .authorizationRequestRepository(authorizationRequestRepository))
                        .redirectionEndpoint(r -> r.baseUri("/api/auth/login/oauth2/code/*"))
                        .userInfoEndpoint(u -> u
                                .oidcUserService(appOidcUserService)
                                .userService(appOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, revokedSessionStore), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
