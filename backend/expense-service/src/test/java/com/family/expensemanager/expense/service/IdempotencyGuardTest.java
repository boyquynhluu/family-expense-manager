package com.family.expensemanager.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.expense.dao.IdempotencyKeyDao;
import com.family.expensemanager.expense.domain.entity.IdempotencyKey;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyGuardTest {

    private static final String SCOPE = "CREATE_TRANSACTION";

    record Body(String note, int amount) {
    }

    record Result(long id) {
    }

    @Mock
    private IdempotencyKeyDao dao;

    private IdempotencyGuard guard;
    private final AtomicInteger runs = new AtomicInteger();

    @BeforeEach
    void setUp() {
        guard = new IdempotencyGuard(dao, new ObjectMapper().findAndRegisterModules());
    }

    private Result action() {
        return new Result(runs.incrementAndGet());
    }

    /** The row the first request with this key left behind (hash and response captured from that run). */
    private IdempotencyKey completedRecordFor(Body body) {
        when(dao.selectByFamilyIdAndScopeAndKey(1L, SCOPE, "k1")).thenReturn(Optional.empty());
        guard.runOnce(1L, SCOPE, "k1", body, Result.class, this::action);
        ArgumentCaptor<IdempotencyKey> saved = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(dao).update(saved.capture());
        return saved.getValue();
    }

    @Test
    void noKey_justRunsTheAction() {
        assertThat(guard.runOnce(1L, SCOPE, null, new Body("a", 1), Result.class, this::action)).isEqualTo(new Result(1));
        assertThat(guard.runOnce(1L, SCOPE, " ", new Body("a", 1), Result.class, this::action)).isEqualTo(new Result(2));
        verify(dao, never()).insert(any());
    }

    @Test
    void retryWithSameKeyAndBody_replaysTheFirstResponse_withoutRunningAgain() {
        IdempotencyKey record = completedRecordFor(new Body("an uong", 50_000));
        when(dao.selectByFamilyIdAndScopeAndKey(1L, SCOPE, "k1")).thenReturn(Optional.of(record));

        Result replayed = guard.runOnce(1L, SCOPE, "k1", new Body("an uong", 50_000), Result.class, this::action);

        assertThat(replayed).isEqualTo(new Result(1));
        assertThat(runs).hasValue(1);
    }

    @Test
    void sameKeyWithADifferentBody_isRefusedWith422() {
        IdempotencyKey record = completedRecordFor(new Body("an uong", 50_000));
        when(dao.selectByFamilyIdAndScopeAndKey(1L, SCOPE, "k1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> guard.runOnce(1L, SCOPE, "k1", new Body("an uong", 90_000), Result.class, this::action))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(runs).hasValue(1);
    }

    @Test
    void recordFromBeforeTheHashColumn_stillReplays() {
        IdempotencyKey legacy = new IdempotencyKey();
        legacy.setResponseJson("{\"id\":42}");
        legacy.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(dao.selectByFamilyIdAndScopeAndKey(1L, SCOPE, "k1")).thenReturn(Optional.of(legacy));

        assertThat(guard.runOnce(1L, SCOPE, "k1", new Body("x", 1), Result.class, this::action)).isEqualTo(new Result(42));
        assertThat(runs).hasValue(0);
    }

    @Test
    void sameKeyStillInFlight_isA409() {
        IdempotencyKey inFlight = new IdempotencyKey();
        inFlight.setCreatedAt(LocalDateTime.now());
        when(dao.selectByFamilyIdAndScopeAndKey(1L, SCOPE, "k1")).thenReturn(Optional.of(inFlight));

        assertThatThrownBy(() -> guard.runOnce(1L, SCOPE, "k1", new Body("a", 1), Result.class, this::action))
                .isInstanceOf(ConflictException.class);
        assertThat(runs).hasValue(0);
    }

    @Test
    void keyLongerThanTheColumn_isA400_notADatabaseError() {
        assertThatThrownBy(() -> guard.runOnce(1L, SCOPE, "k".repeat(256), new Body("a", 1), Result.class, this::action))
                .isInstanceOf(BadRequestException.class);
        verify(dao, never()).insert(any());
    }

    @Test
    void failedAction_releasesTheKey_soARetryCanRunIt() {
        when(dao.selectByFamilyIdAndScopeAndKey(1L, SCOPE, "k1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.runOnce(1L, SCOPE, "k1", new Body("a", 1), Result.class, () -> {
            throw new BadRequestException("invalid");
        })).isInstanceOf(BadRequestException.class);

        verify(dao).delete(any(IdempotencyKey.class));
    }
}
