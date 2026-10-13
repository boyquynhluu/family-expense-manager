package com.family.expensemanager.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.mail.MailHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Starts the real mail + mail-health auto-configuration with the wrapper in place, as in production (where
 * spring.mail.host is set). Regression: the wrapper hid the JavaMailSenderImpl bean, so the mail health
 * contributor failed startup and notification-service crash-looped.
 */
class ResilientMailSenderContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class,
                    HealthContributorAutoConfiguration.class, MailHealthContributorAutoConfiguration.class))
            .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
            .withBean(ResilientMailSenderPostProcessor.class)
            .withPropertyValues("spring.mail.host=smtp.example.com");

    @Test
    void startsWithTheApplicationYmlSettings_andTheMailSenderIsWrapped() {
        runner.withPropertyValues("management.health.mail.enabled=" + applicationYml("management.health.mail.enabled"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JavaMailSender.class))
                            .isInstanceOf(ResilientMailSenderPostProcessor.CircuitBreakingMailSender.class);
                });
    }

    @Test
    void mailHealthCheckEnabled_failsStartup_whichIsWhyApplicationYmlTurnsItOff() {
        runner.withPropertyValues("management.health.mail.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String applicationYml(String key) {
        try {
            for (PropertySource<?> source : new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"))) {
                Object value = source.getProperty(key);
                if (value != null) {
                    return value.toString();
                }
            }
            return "true"; // Spring Boot's default
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
