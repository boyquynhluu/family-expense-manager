package com.family.expensemanager.auth;

import com.family.expensemanager.common.config.OpenApiConfig;
import com.family.expensemanager.common.exception.GlobalExceptionHandler;
import com.family.expensemanager.common.message.Messages;
import com.family.expensemanager.common.security.JwtUtil;
import com.family.expensemanager.common.security.RevokedSessionStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

@EnableDiscoveryClient
@Import({OpenApiConfig.class, GlobalExceptionHandler.class, JwtUtil.class, RevokedSessionStore.class, Messages.class})
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
