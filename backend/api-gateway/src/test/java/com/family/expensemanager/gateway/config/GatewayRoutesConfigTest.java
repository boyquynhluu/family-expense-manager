package com.family.expensemanager.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Every route bean builds (path predicate + circuit breaker + load balancer filters) without a running context. */
class GatewayRoutesConfigTest {

    private final GatewayRoutesConfig config = new GatewayRoutesConfig();

    @Test
    void allRoutesBuild() {
        assertThat(config.authServiceDocsRoute()).isNotNull();
        assertThat(config.expenseServiceDocsRoute()).isNotNull();
        assertThat(config.notificationServiceDocsRoute()).isNotNull();
        assertThat(config.authServiceRoute()).isNotNull();
        assertThat(config.authServiceAdminRoute()).isNotNull();
        assertThat(config.expenseServiceRoute()).isNotNull();
        assertThat(config.notificationServiceRoute()).isNotNull();
    }
}
