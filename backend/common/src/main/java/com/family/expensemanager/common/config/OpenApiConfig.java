package com.family.expensemanager.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Swagger/OpenAPI setup: exposes each service's title from
 * {@code spring.application.name} and registers the JWT bearer scheme so
 * Swagger UI's "Authorize" button can attach {@code Authorization: Bearer <token>}.
 *
 * Not auto-scanned by services (it lives outside their base package) — import it
 * explicitly on the main application class, e.g. {@code @Import(OpenApiConfig.class)}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi(@Value("${spring.application.name}") String serviceName) {
        return new OpenAPI()
                .info(new Info()
                        .title(serviceName)
                        .version("v1")
                        .description("Family Expense Manager - " + serviceName))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
