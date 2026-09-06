package com.family.expensemanager.notification;

import com.family.expensemanager.common.config.OpenApiConfig;
import com.family.expensemanager.common.exception.GlobalExceptionHandler;
import com.family.expensemanager.common.security.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;

@EnableDiscoveryClient
@EnableKafka
@Import({OpenApiConfig.class, GlobalExceptionHandler.class, JwtUtil.class})
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
