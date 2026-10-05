package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.PeriodLockDao;
import com.family.expensemanager.expense.dao.PeriodLockLogDao;
import com.family.expensemanager.expense.domain.entity.PeriodLock;
import com.family.expensemanager.expense.domain.entity.PeriodLockLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodLockServiceTest {

    // "Today" is 2026-10-05, so 2026-09 and earlier are past months.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private PeriodLockDao periodLockDao;
    @Mock
    private PeriodLockLogDao periodLockLogDao;

    private PeriodLockService service;

    @BeforeEach
    void setUp() {
        service = new PeriodLockService(periodLockDao, periodLockLogDao, CLOCK);
    }

    @Test
    void lock_closesAPastMonth_andLogsWhoDidIt() {
        when(periodLockDao.selectByFamilyAndMonth(1L, "2026-09")).thenReturn(Optional.empty());

        var response = service.lock(1L, "2026-09", 7L, "Chủ hộ");

        assertThat(response.periodMonth()).isEqualTo("2026-09");
        assertThat(response.lockedByName()).isEqualTo("Chủ hộ");
        verify(periodLockDao).insert(any(PeriodLock.class));
        ArgumentCaptor<PeriodLockLog> log = ArgumentCaptor.forClass(PeriodLockLog.class);
        verify(periodLockLogDao).insert(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("LOCKED");
        assertThat(log.getValue().getActorUserId()).isEqualTo(7L);
    }

    @Test
    void lock_refusesTheCurrentAndFutureMonths() {
        assertThatThrownBy(() -> service.lock(1L, "2026-10", 7L, "Chủ hộ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.lock(1L, "2026-11", 7L, "Chủ hộ")).isInstanceOf(BadRequestException.class);
        verify(periodLockDao, never()).insert(any());
    }

    @Test
    void lock_refusesAMalformedMonth() {
        assertThatThrownBy(() -> service.lock(1L, "2026-13", 7L, "Chủ hộ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.lock(1L, null, 7L, "Chủ hộ")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void lock_refusesAMonthThatIsAlreadyClosed() {
        when(periodLockDao.selectByFamilyAndMonth(1L, "2026-09")).thenReturn(Optional.of(new PeriodLock()));

        assertThatThrownBy(() -> service.lock(1L, "2026-09", 7L, "Chủ hộ")).isInstanceOf(ConflictException.class);
        verify(periodLockDao, never()).insert(any());
        verifyNoInteractions(periodLockLogDao);
    }

    @Test
    void unlock_reopensTheMonth_andLogsIt() {
        PeriodLock lock = new PeriodLock();
        when(periodLockDao.selectByFamilyAndMonth(1L, "2026-09")).thenReturn(Optional.of(lock));

        service.unlock(1L, "2026-09", 7L, "Chủ hộ");

        verify(periodLockDao).delete(lock);
        ArgumentCaptor<PeriodLockLog> log = ArgumentCaptor.forClass(PeriodLockLog.class);
        verify(periodLockLogDao).insert(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("UNLOCKED");
    }

    @Test
    void unlock_answers404_forAMonthThatIsNotClosed() {
        when(periodLockDao.selectByFamilyAndMonth(1L, "2026-09")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlock(1L, "2026-09", 7L, "Chủ hộ")).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(periodLockLogDao);
    }

    @Test
    void requireUnlocked_passes_whenNoneOfTheMonthsIsClosed() {
        when(periodLockDao.countByFamilyAndMonths(eq(1L), anyList())).thenReturn(0L);

        assertThatCode(() -> service.requireUnlocked(1L, LocalDateTime.of(2026, 9, 1, 0, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void requireUnlocked_checksEachDistinctMonthOnce_andIgnoresNulls() {
        when(periodLockDao.countByFamilyAndMonths(eq(1L), anyList())).thenReturn(0L);

        service.requireUnlocked(1L, LocalDateTime.of(2026, 9, 1, 0, 0), null, LocalDateTime.of(2026, 9, 30, 23, 59),
                LocalDateTime.of(2026, 8, 15, 12, 0));

        verify(periodLockDao).countByFamilyAndMonths(1L, List.of("2026-09", "2026-08"));
    }

    @Test
    void requireUnlocked_throws409NamingTheClosedMonth_whenOneOfTheMonthsIsClosed() {
        when(periodLockDao.countByFamilyAndMonths(eq(1L), anyList())).thenReturn(1L);
        when(periodLockDao.selectByFamilyAndMonth(1L, "2026-10")).thenReturn(Optional.empty());
        when(periodLockDao.selectByFamilyAndMonth(1L, "2026-08")).thenReturn(Optional.of(new PeriodLock()));

        // An edit moving a transaction from October back into a closed August.
        assertThatThrownBy(() -> service.requireUnlocked(
                1L, LocalDateTime.of(2026, 10, 2, 9, 0), LocalDateTime.of(2026, 8, 20, 9, 0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2026-08");
    }

    @Test
    void requireUnlocked_doesNothing_withoutDates() {
        service.requireUnlocked(1L, (LocalDateTime) null);

        verifyNoInteractions(periodLockDao);
    }
}
