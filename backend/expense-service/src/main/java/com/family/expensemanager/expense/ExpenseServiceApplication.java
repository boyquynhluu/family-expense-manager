package com.family.expensemanager.expense;

import com.family.expensemanager.common.config.OpenApiConfig;
import com.family.expensemanager.common.exception.GlobalExceptionHandler;
import com.family.expensemanager.common.security.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

@EnableDiscoveryClient
@Import({OpenApiConfig.class, GlobalExceptionHandler.class, JwtUtil.class})
@SpringBootApplication
public class ExpenseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseServiceApplication.class, args);
    }
}
