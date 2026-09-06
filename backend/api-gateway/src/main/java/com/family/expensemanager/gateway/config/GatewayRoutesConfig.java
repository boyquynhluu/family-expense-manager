package com.family.expensemanager.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Routes for Spring Cloud Gateway Server MVC (servlet-based). Unlike the reactive
 * gateway, the MVC variant has no routes-as-YAML support — routes are Java
 * {@link RouterFunction} beans, and load balancing goes through
 * {@code LoadBalancerFilterFunctions.lb(serviceId)} as a {@code .filter(...)} (there is
 * no {@code lb://} URI shortcut like the reactive gateway). Each proxy route matches
 * every HTTP method under its path prefix and forwards unchanged (no StripPrefix) —
 * see README "Bảo mật" / auth-service, expense-service, notification-service
 * controllers, which all listen on the same {@code /api/<service>/...} paths for that
 * reason.
 */
@Configuration
public class GatewayRoutesConfig {

    // Docs routes are registered before the catch-all "/api/<service>/**" routes below:
    // Spring Cloud Gateway MVC combines RouterFunction beans in declaration order and
    // uses the first match, so the more specific docs route must come first or the
    // catch-all swallows it (forwarding the un-rewritten path, which 403s downstream
    // since it isn't in that service's permitAll list).
    @Bean
    public RouterFunction<ServerResponse> authServiceDocsRoute() {
        return route("auth-service-docs")
                .route(path("/api/auth/v3/api-docs"), http())
                .before(rewritePath("/api/auth/v3/api-docs", "/v3/api-docs"))
                .filter(lb("auth-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> expenseServiceDocsRoute() {
        return route("expense-service-docs")
                .route(path("/api/expenses/v3/api-docs"), http())
                .before(rewritePath("/api/expenses/v3/api-docs", "/v3/api-docs"))
                .filter(lb("expense-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> notificationServiceDocsRoute() {
        return route("notification-service-docs")
                .route(path("/api/notifications/v3/api-docs"), http())
                .before(rewritePath("/api/notifications/v3/api-docs", "/v3/api-docs"))
                .filter(lb("notification-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> authServiceRoute() {
        return route("auth-service")
                .route(path("/api/auth/**"), http())
                .filter(lb("auth-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> expenseServiceRoute() {
        return route("expense-service")
                .route(path("/api/expenses/**"), http())
                .filter(lb("expense-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> notificationServiceRoute() {
        return route("notification-service")
                .route(path("/api/notifications/**"), http())
                .filter(lb("notification-service"))
                .build();
    }
}
