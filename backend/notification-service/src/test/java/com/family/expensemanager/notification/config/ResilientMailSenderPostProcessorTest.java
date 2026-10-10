package com.family.expensemanager.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.family.expensemanager.notification.config.ResilientMailSenderPostProcessor.CircuitBreakingMailSender;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;

class ResilientMailSenderPostProcessorTest {

    private JavaMailSender delegate;
    private CircuitBreaker breaker;
    private CircuitBreakingMailSender sender;
    private final MimeMessage message = new MimeMessage((jakarta.mail.Session) null);

    @BeforeEach
    void setUp() {
        delegate = mock(JavaMailSender.class);
        // Same shape as application.yml's "smtp" instance, small enough to trip in a test.
        breaker = CircuitBreaker.of("smtp", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(new SmtpFailurePredicate())
                .build());
        sender = new CircuitBreakingMailSender(delegate, () -> breaker);
    }

    @Test
    void postProcessor_wrapsOnlyMailSenders() {
        ResilientMailSenderPostProcessor processor = new ResilientMailSenderPostProcessor(null);
        assertThat(processor.postProcessAfterInitialization(delegate, "mailSender"))
                .isInstanceOf(CircuitBreakingMailSender.class);
        Object other = new Object();
        assertThat(processor.postProcessAfterInitialization(other, "other")).isSameAs(other);
    }

    @Test
    void smtpDown_opensTheBreaker_thenSendsFailFastWithAMailSendException() {
        doThrow(new MailSendException("connect timed out")).when(delegate).send(message);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> sender.send(message)).isInstanceOf(MailSendException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        org.mockito.Mockito.clearInvocations(delegate);
        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(MailSendException.class) // listeners keep handling it like any SMTP error
                .hasCauseInstanceOf(CallNotPermittedException.class);
        verify(delegate, never()).send(message);
    }

    @Test
    void rejectedRecipients_doNotOpenTheBreaker() {
        doThrow(new MailSendException("bad address", new SendFailedException("550 no such user")))
                .when(delegate).send(message);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> sender.send(message)).isInstanceOf(MailSendException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void smtpFailurePredicate_ignoresSendFailedAnywhereInTheCauseChain() {
        SmtpFailurePredicate predicate = new SmtpFailurePredicate();
        assertThat(predicate.test(new MailSendException("timeout"))).isTrue();
        assertThat(predicate.test(new MailSendException("x", new RuntimeException(new SendFailedException("550"))))).isFalse();
    }
}
