package com.family.expensemanager.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.family.expensemanager.expense.dao.TransactionAuditLogDao;
import com.family.expensemanager.expense.domain.entity.TransactionAuditLog;
import com.family.expensemanager.expense.dto.TransactionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TransactionAuditServiceTest {

    @Mock
    private TransactionAuditLogDao auditLogDao;

    private TransactionAuditService service;

    private final TransactionSnapshot before = new TransactionSnapshot(
            5L, 7L, "EXPENSE", new BigDecimal("150000.00"), LocalDateTime.of(2026, 9, 12, 8, 30), "Tiền chợ", false);
    private final TransactionSnapshot after = new TransactionSnapshot(
            5L, 7L, "EXPENSE", new BigDecimal("175000.00"), LocalDateTime.of(2026, 9, 12, 8, 30), "Tiền chợ", true);

    @BeforeEach
    void setUp() {
        service = new TransactionAuditService(auditLogDao, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void record_storesWhoWhatAndBothStates_andListHistoryReadsThemBack() {
        service.record(TransactionAuditService.ACTION_UPDATED, 1L, 42L, before, after, 10L, "An");

        ArgumentCaptor<TransactionAuditLog> captor = ArgumentCaptor.forClass(TransactionAuditLog.class);
        verify(auditLogDao).insert(captor.capture());
        TransactionAuditLog saved = captor.getValue();
        assertThat(saved.getFamilyId()).isEqualTo(1L);
        assertThat(saved.getTransactionId()).isEqualTo(42L);
        assertThat(saved.getAction()).isEqualTo("UPDATED");
        assertThat(saved.getActorUserId()).isEqualTo(10L);
        assertThat(saved.getActorName()).isEqualTo("An");
        assertThat(saved.getCreatedAt()).isNotNull();

        // Round trip through the stored JSON — what the history endpoint will return.
        when(auditLogDao.selectByTransactionIdAndFamilyId(42L, 1L)).thenReturn(List.of(saved));
        var history = service.listHistory(1L, 42L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).before()).isEqualTo(before);
        assertThat(history.get(0).after()).isEqualTo(after);
    }

    @Test
    void record_leavesTheMissingSideNull_forCreatedAndDeleted() {
        service.record(TransactionAuditService.ACTION_DELETED, 1L, 42L, before, null, 10L, "An");

        ArgumentCaptor<TransactionAuditLog> captor = ArgumentCaptor.forClass(TransactionAuditLog.class);
        verify(auditLogDao).insert(captor.capture());
        assertThat(captor.getValue().getBeforeJson()).isNotBlank();
        assertThat(captor.getValue().getAfterJson()).isNull();
    }

    @Test
    void record_truncatesAnOverlongActorName_toTheColumnSize() {
        service.record(TransactionAuditService.ACTION_CREATED, 1L, 42L, null, after, 10L, "x".repeat(150));

        ArgumentCaptor<TransactionAuditLog> captor = ArgumentCaptor.forClass(TransactionAuditLog.class);
        verify(auditLogDao).insert(captor.capture());
        assertThat(captor.getValue().getActorName()).hasSize(100);
    }

    @Test
    void listHistory_isScopedToTheCallersFamily() {
        when(auditLogDao.selectByTransactionIdAndFamilyId(42L, 2L)).thenReturn(List.of());

        assertThat(service.listHistory(2L, 42L)).isEmpty();
        verify(auditLogDao).selectByTransactionIdAndFamilyId(42L, 2L);
    }
}
