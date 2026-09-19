package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.dto.TransactionReportFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionDao transactionDao;
    @Mock
    private BudgetDao budgetDao;
    @Mock
    private WalletService walletService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private ReceiptStorageService receiptStorageService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionDao, budgetDao, walletService, categoryService, eventPublisher, cacheManager,
                receiptStorageService);
    }

    @Test
    void listByFamilyPaged_passesFilterAndOffsetThrough_toTheDao() {
        TransactionReportFilter filter = new TransactionReportFilter(5L, 7L, "EXPENSE", null, null);
        when(transactionDao.countByFamilyIdFiltered(1L, 5L, 7L, "EXPENSE", null, null)).thenReturn(45L);
        when(transactionDao.selectByFamilyIdFiltered(1L, 5L, 7L, "EXPENSE", null, null, 20, 40))
                .thenReturn(List.of(transaction(99L)));

        var page = transactionService.listByFamilyPaged(1L, filter, 2, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo(99L);
        assertThat(page.totalElements()).isEqualTo(45);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenPageNegative() {
        TransactionReportFilter filter = new TransactionReportFilter(null, null, null, null, null);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, filter, -1, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenSizeOutOfRange() {
        TransactionReportFilter filter = new TransactionReportFilter(null, null, null, null, null);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, filter, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, filter, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadReceipt_savesFile_andUpdatesTransaction() throws Exception {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(receiptStorageService.save(eq(1L), eq(1L), any())).thenReturn("1/1-123.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", new byte[] {1, 2, 3});

        var response = transactionService.uploadReceipt(1L, 1L, file);

        assertThat(response.hasReceipt()).isTrue();
        assertThat(target.getReceiptPath()).isEqualTo("1/1-123.jpg");
        assertThat(target.getReceiptContentType()).isEqualTo("image/jpeg");
        verify(transactionDao).update(target);
        verify(receiptStorageService, never()).delete(any());
    }

    @Test
    void uploadReceipt_deletesOldFile_whenReplacingExistingReceipt() throws Exception {
        Transaction target = transaction(1L);
        target.setReceiptPath("1/1-old.jpg");
        target.setReceiptContentType("image/jpeg");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(receiptStorageService.save(eq(1L), eq(1L), any())).thenReturn("1/1-new.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", new byte[] {1});

        transactionService.uploadReceipt(1L, 1L, file);

        verify(receiptStorageService).delete("1/1-old.jpg");
    }

    @Test
    void uploadReceipt_throwsBadRequest_whenContentTypeNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "note.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> transactionService.uploadReceipt(1L, 1L, file))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).selectById(any());
    }

    @Test
    void getReceipt_returnsContent_whenReceiptExists() throws Exception {
        Transaction target = transaction(1L);
        target.setReceiptPath("1/1-a.jpg");
        target.setReceiptContentType("image/jpeg");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(receiptStorageService.read("1/1-a.jpg")).thenReturn(new byte[] {9, 9});

        var receipt = transactionService.getReceipt(1L, 1L);

        assertThat(receipt.content()).containsExactly(9, 9);
        assertThat(receipt.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void getReceipt_throwsNotFound_whenNoReceiptAttached() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transactionService.getReceipt(1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteReceipt_clearsTransaction_andDeletesFile() {
        Transaction target = transaction(1L);
        target.setReceiptPath("1/1-a.jpg");
        target.setReceiptContentType("image/jpeg");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.deleteReceipt(1L, 1L);

        assertThat(target.getReceiptPath()).isNull();
        assertThat(target.getReceiptContentType()).isNull();
        verify(transactionDao).update(target);
        verify(receiptStorageService).delete("1/1-a.jpg");
    }

    @Test
    void deleteReceipt_isNoOp_whenNoReceiptAttached() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.deleteReceipt(1L, 1L);

        verify(transactionDao, never()).update(any());
        verify(receiptStorageService, never()).delete(any());
    }

    @Test
    void delete_softDeletesRow_andKeepsReceiptFile() {
        Transaction target = transaction(1L);
        target.setReceiptPath("1/1-a.jpg");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.delete(1L, 1L);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(transactionDao).update(target);
        verify(transactionDao, never()).delete(any());
        verify(receiptStorageService, never()).delete(any());
    }

    @Test
    void restore_clearsDeletedAt_whenRowExists() {
        Transaction restored = transaction(1L);
        when(transactionDao.restore(1L, 1L)).thenReturn(1);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(restored));

        transactionService.restore(1L, 1L);

        verify(transactionDao).restore(1L, 1L);
    }

    @Test
    void restore_throwsNotFound_whenRowMissingOrNotDeleted() {
        when(transactionDao.restore(1L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> transactionService.restore(1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listDeleted_returnsDeletedTransactions() {
        when(transactionDao.selectDeletedByFamilyId(1L)).thenReturn(List.of(transaction(99L)));

        var result = transactionService.listDeleted(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(99L);
    }

    private static Transaction transaction(Long id) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setFamilyId(1L);
        transaction.setWalletId(5L);
        transaction.setCategoryId(7L);
        transaction.setType("EXPENSE");
        transaction.setAmount(BigDecimal.TEN);
        transaction.setOccurredAt(LocalDateTime.now());
        return transaction;
    }
}
