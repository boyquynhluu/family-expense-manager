package com.family.expensemanager.notification.config;

import java.io.InputStream;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.mail.internet.MimeMessage;

/**
 * Wraps the auto-configured {@link JavaMailSender} so every send goes through the "smtp" circuit breaker
 * (thresholds in application.yml; what counts as a failure in {@link SmtpFailurePredicate}). A post-processor
 * rather than a JavaMailSender bean of our own: declaring one would switch off Spring Boot's mail
 * auto-configuration (spring.mail.*).
 *
 * <p>While the breaker is open a send fails at once with a {@link MailSendException} instead of waiting for the
 * SMTP timeouts — so every listener keeps its existing handling unchanged: the ones that catch MailException
 * log and move on, the ones that let it propagate send the event through their @RetryableTopic retries (and,
 * if SMTP is still down when those run out, to the DLT, from where it can be replayed).
 *
 * @author boyquynhluu
 */
@Component
public class ResilientMailSenderPostProcessor implements BeanPostProcessor {

    static final String CIRCUIT_BREAKER = "smtp";

    // Resolved lazily: a BeanPostProcessor is created before ordinary beans like the registry.
    private final ObjectProvider<CircuitBreakerRegistry> registry;

    public ResilientMailSenderPostProcessor(ObjectProvider<CircuitBreakerRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JavaMailSender sender && !(bean instanceof CircuitBreakingMailSender)) {
            return new CircuitBreakingMailSender(sender, () -> registry.getObject().circuitBreaker(CIRCUIT_BREAKER));
        }
        return bean;
    }

    /** Delegates to the real sender; only the sends (not message creation) go through the breaker. */
    static final class CircuitBreakingMailSender implements JavaMailSender {

        private final JavaMailSender delegate;
        private final Supplier<CircuitBreaker> breaker;

        CircuitBreakingMailSender(JavaMailSender delegate, Supplier<CircuitBreaker> breaker) {
            this.delegate = delegate;
            this.breaker = breaker;
        }

        private void guarded(Runnable send) {
            try {
                breaker.get().executeRunnable(send);
            } catch (CallNotPermittedException e) {
                throw new MailSendException("SMTP tạm ngưng sau nhiều lần gửi lỗi liên tiếp (circuit breaker đang mở)", e);
            }
        }

        @Override
        public MimeMessage createMimeMessage() {
            return delegate.createMimeMessage();
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            return delegate.createMimeMessage(contentStream);
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            guarded(() -> delegate.send(mimeMessage));
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            guarded(() -> delegate.send(mimeMessages));
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            guarded(() -> delegate.send(mimeMessagePreparator));
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            guarded(() -> delegate.send(mimeMessagePreparators));
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            guarded(() -> delegate.send(simpleMessage));
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            guarded(() -> delegate.send(simpleMessages));
        }
    }
}
