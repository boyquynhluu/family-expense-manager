package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long CREATOR_ID = 10L;
    private static final Long OTHER_USER_ID = 11L;

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
        TransactionReportFilter filter = new TransactionReportFilter(
                5L, 7L, "EXPENSE", null, null, "an sang", new BigDecimal("10"), new BigDecimal("500"));
        when(transactionDao.countByFamilyIdFiltered(
                1L, 5L, 7L, "EXPENSE", null, null, "%an sang%", new BigDecimal("10"), new BigDecimal("500"))).thenReturn(45L);
        when(transactionDao.selectByFamilyIdFiltered(
                1L, 5L, 7L, "EXPENSE", null, null, "%an sang%", new BigDecimal("10"), new BigDecimal("500"), 20, 40))
                .thenReturn(List.of(transaction(99L)));

        var page = transactionService.listByFamilyPaged(1L, filter, 2, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo(99L);
        assertThat(page.content().get(0).userId()).isEqualTo(CREATOR_ID);
        assertThat(page.totalElements()).isEqualTo(45);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenPageNegative() {
        TransactionReportFilter filter = new TransactionReportFilter(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, filter, -1, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenSizeOutOfRange() {
        TransactionReportFilter filter = new TransactionReportFilter(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, filter, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, filter, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_savesChanges_whenMemberEditsOwnTransaction() {
        Transaction target = transaction(1L);
        stubUpdateDependencies(target);

        var response = transactionService.update(1L, 1L, CREATOR_ID, false, updateRequest());

        assertThat(response.amount()).isEqualByComparingTo("99");
        verify(transactionDao).update(target);
    }

    @Test
    void update_throwsForbidden_whenMemberEditsSomeoneElsesTransaction() {
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(transaction(1L)));

        assertForbidden(() -> transactionService.update(1L, 1L, OTHER_USER_ID, false, updateRequest()));
        verify(transactionDao, never()).update(any());
    }

    @Test
    void update_succeeds_whenOwnerEditsSomeoneElsesTransaction() {
        Transaction target = transaction(1L);
        stubUpdateDependencies(target);

        transactionService.update(1L, 1L, OTHER_USER_ID, true, updateRequest());

        verify(transactionDao).update(target);
    }

    @Test
    void uploadReceipt_savesFile_andUpdatesTransaction() throws Exception {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(receiptStorageService.save(eq(1L), eq(1L), any())).thenReturn("1/1-123.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", new byte[] {1, 2, 3});

        var response = transactionService.uploadReceipt(1L, 1L, CREATOR_ID, false, file);

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

        transactionService.uploadReceipt(1L, 1L, CREATOR_ID, false, file);

        verify(receiptStorageService).delete("1/1-old.jpg");
    }

    @Test
    void uploadReceipt_throwsBadRequest_whenContentTypeNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "note.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> transactionService.uploadReceipt(1L, 1L, CREATOR_ID, false, file))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).selectById(any());
    }

    @Test
    void uploadReceipt_throwsForbidden_whenMemberAttachesToSomeoneElsesTransaction() throws Exception {
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(transaction(1L)));
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", new byte[] {1});

        assertForbidden(() -> transactionService.uploadReceipt(1L, 1L, OTHER_USER_ID, false, file));
        verify(receiptStorageService, never()).save(any(), any(), any());
        verify(transactionDao, never()).update(any());
    }

    @Test
    void uploadReceipt_succeeds_whenOwnerAttachesToSomeoneElsesTransaction() throws Exception {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(receiptStorageService.save(eq(1L), eq(1L), any())).thenReturn("1/1-123.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", new byte[] {1});

        transactionService.uploadReceipt(1L, 1L, OTHER_USER_ID, true, file);

        assertThat(target.getReceiptPath()).isEqualTo("1/1-123.jpg");
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

        transactionService.deleteReceipt(1L, 1L, CREATOR_ID, false);

        assertThat(target.getReceiptPath()).isNull();
        assertThat(target.getReceiptContentType()).isNull();
        verify(transactionDao).update(target);
        verify(receiptStorageService).delete("1/1-a.jpg");
    }

    @Test
    void deleteReceipt_isNoOp_whenNoReceiptAttached() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.deleteReceipt(1L, 1L, CREATOR_ID, false);

        verify(transactionDao, never()).update(any());
        verify(receiptStorageService, never()).delete(any());
    }

    @Test
    void deleteReceipt_throwsForbidden_whenMemberDeletesSomeoneElsesReceipt() {
        Transaction target = transaction(1L);
        target.setReceiptPath("1/1-a.jpg");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        assertForbidden(() -> transactionService.deleteReceipt(1L, 1L, OTHER_USER_ID, false));
        assertThat(target.getReceiptPath()).isEqualTo("1/1-a.jpg");
        verify(receiptStorageService, never()).delete(any());
    }

    @Test
    void deleteReceipt_succeeds_whenOwnerDeletesSomeoneElsesReceipt() {
        Transaction target = transaction(1L);
        target.setReceiptPath("1/1-a.jpg");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.deleteReceipt(1L, 1L, OTHER_USER_ID, true);

        assertThat(target.getReceiptPath()).isNull();
        verify(receiptStorageService).delete("1/1-a.jpg");
    }

    @Test
    void delete_softDeletesRow_andKeepsReceiptFile() {
        Transaction target = transaction(1L);
        target.setReceiptPath("1/1-a.jpg");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.delete(1L, 1L, CREATOR_ID, false);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(transactionDao).update(target);
        verify(transactionDao, never()).delete(any());
        verify(receiptStorageService, never()).delete(any());
    }

    @Test
    void delete_throwsForbidden_whenMemberDeletesSomeoneElsesTransaction() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        assertForbidden(() -> transactionService.delete(1L, 1L, OTHER_USER_ID, false));
        assertThat(target.getDeletedAt()).isNull();
        verify(transactionDao, never()).update(any());
    }

    @Test
    void delete_softDeletes_whenOwnerDeletesSomeoneElsesTransaction() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.delete(1L, 1L, OTHER_USER_ID, true);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(transactionDao).update(target);
    }

    @Test
    void restore_clearsDeletedAt_whenMemberRestoresOwnTransaction() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(transaction(1L)));
        when(transactionDao.restore(1L, 1L)).thenReturn(1);

        transactionService.restore(1L, 1L, CREATOR_ID, false);

        verify(transactionDao).restore(1L, 1L);
    }

    @Test
    void restore_throwsNotFound_whenRowMissingOrNotDeleted() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.restore(1L, 1L, CREATOR_ID, false))
                .isInstanceOf(NotFoundException.class);
        verify(transactionDao, never()).restore(any(), any());
    }

    @Test
    void restore_throwsNotFound_whenDeletedRowBelongsToAnotherFamily() {
        Transaction deleted = transaction(1L);
        deleted.setFamilyId(2L);
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> transactionService.restore(1L, 1L, CREATOR_ID, true))
                .isInstanceOf(NotFoundException.class);
        verify(transactionDao, never()).restore(any(), any());
    }

    @Test
    void restore_throwsForbidden_whenMemberRestoresSomeoneElsesTransaction() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(transaction(1L)));

        assertForbidden(() -> transactionService.restore(1L, 1L, OTHER_USER_ID, false));
        verify(transactionDao, never()).restore(any(), any());
    }

    @Test
    void restore_succeeds_whenOwnerRestoresSomeoneElsesTransaction() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(transaction(1L)));
        when(transactionDao.restore(1L, 1L)).thenReturn(1);

        transactionService.restore(1L, 1L, OTHER_USER_ID, true);

        verify(transactionDao).restore(1L, 1L);
    }

    @Test
    void listDeletedPaged_returnsPageWithOffset() {
        when(transactionDao.countDeletedByFamilyId(1L)).thenReturn(12L);
        when(transactionDao.selectDeletedByFamilyIdPaged(1L, 5, 10))
                .thenReturn(List.of(transaction(98L), transaction(99L)));

        var result = transactionService.listDeletedPaged(1L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(1).id()).isEqualTo(99L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listDeletedPaged_rejectsNegativePage() {
        assertThatThrownBy(() -> transactionService.listDeletedPaged(1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listDeletedPaged_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> transactionService.listDeletedPaged(1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transactionService.listDeletedPaged(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    private void stubUpdateDependencies(Transaction target) {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        Category category = new Category();
        category.setId(7L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet);
        when(categoryService.requireOwnedByFamily(7L, 1L)).thenReturn(category);
    }

    private static TransactionRequest updateRequest() {
        return new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("99"), LocalDateTime.now(), "sửa");
    }

    private static void assertForbidden(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static Transaction transaction(Long id) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setFamilyId(1L);
        transaction.setUserId(CREATOR_ID);
        transaction.setWalletId(5L);
        transaction.setCategoryId(7L);
        transaction.setType("EXPENSE");
        transaction.setAmount(BigDecimal.TEN);
        transaction.setOccurredAt(LocalDateTime.now());
        return transaction;
    }

    @Test
    void create_publishesBudgetWarning_whenCategorySpendingCrossesEightyPercent() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                budget(7L, "1000"), "700", null, null, "100");

        assertThat(events).hasSize(1);
        ExpenseEvent warning = events.get(0);
        assertThat(warning.eventType()).isEqualTo(ExpenseEvent.BUDGET_WARNING);
        assertThat(warning.categoryId()).isEqualTo(7L);
        assertThat(warning.categoryName()).isEqualTo("Ăn uống");
        assertThat(warning.totalSpent()).isEqualByComparingTo("800");
        assertThat(warning.limitAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void create_publishesOnlyBudgetExceeded_whenOneTransactionJumpsFromBelowEightyPercentPastLimit() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                budget(7L, "1000"), "700", null, null, "400");

        assertThat(events).extracting(ExpenseEvent::eventType).containsExactly(ExpenseEvent.BUDGET_EXCEEDED);
    }

    @Test
    void create_publishesNothing_whenSpendingWasAlreadyAboveEightyPercent() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                budget(7L, "1000"), "850", null, null, "50");

        assertThat(events).isEmpty();
    }

    @Test
    void create_publishesNothing_whenSpendingStaysBelowEightyPercent() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                budget(7L, "1000"), "100", null, null, "100");

        assertThat(events).isEmpty();
    }

    @Test
    void create_publishesBudgetWarning_whenSpendingLandsExactlyOnLimit() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                budget(7L, "1000"), "700", null, null, "300");

        assertThat(events).extracting(ExpenseEvent::eventType).containsExactly(ExpenseEvent.BUDGET_WARNING);
    }

    @Test
    void create_publishesOverallBudgetWarning_withNullCategory() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                null, "0", overallBudget("1000"), "850", "100");

        assertThat(events).hasSize(1);
        ExpenseEvent warning = events.get(0);
        assertThat(warning.eventType()).isEqualTo(ExpenseEvent.BUDGET_WARNING);
        assertThat(warning.categoryId()).isNull();
        assertThat(warning.categoryName()).isEqualTo("Tổng chi tiêu");
        assertThat(warning.totalSpent()).isEqualByComparingTo("850");
    }

    @Test
    void create_publishesOverallBudgetExceeded_withNullCategory() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                null, "0", overallBudget("1000"), "1050", "100");

        assertThat(events).hasSize(1);
        ExpenseEvent exceeded = events.get(0);
        assertThat(exceeded.eventType()).isEqualTo(ExpenseEvent.BUDGET_EXCEEDED);
        assertThat(exceeded.categoryId()).isNull();
        assertThat(exceeded.categoryName()).isEqualTo("Tổng chi tiêu");
        assertThat(exceeded.totalSpent()).isEqualByComparingTo("1050");
    }

    @Test
    void create_publishesNothing_whenOverallBudgetWasAlreadyExceeded() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                null, "0", overallBudget("1000"), "1200", "100");

        assertThat(events).isEmpty();
    }

    @Test
    void create_publishesBothCategoryAndOverallEvents_whenBothBudgetsCrossed() {
        List<ExpenseEvent> events = createExpenseAndCollectBudgetEvents(
                budget(7L, "1000"), "950", overallBudget("5000"), "4050", "100");

        assertThat(events).extracting(ExpenseEvent::eventType)
                .containsExactly(ExpenseEvent.BUDGET_EXCEEDED, ExpenseEvent.BUDGET_WARNING);
        assertThat(events).extracting(ExpenseEvent::categoryId).containsExactly(7L, null);
    }

    private List<ExpenseEvent> createExpenseAndCollectBudgetEvents(
            Budget categoryBudget, String categoryTotalBefore, Budget overallBudget, String familyTotalAfter,
            String amount) {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 1, 15, 10, 0);
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        Category category = new Category();
        category.setId(7L);
        category.setName("Ăn uống");
        when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet);
        when(categoryService.requireOwnedByFamily(7L, 1L)).thenReturn(category);
        when(transactionDao.sumAmountByCategoryPeriodAndType(1L, 7L, "2026-01", "EXPENSE"))
                .thenReturn(new BigDecimal(categoryTotalBefore));
        when(budgetDao.selectByCategoryAndPeriod(7L, "2026-01")).thenReturn(Optional.ofNullable(categoryBudget));
        when(budgetDao.selectOverallByPeriod(1L, "2026-01")).thenReturn(Optional.ofNullable(overallBudget));
        if (overallBudget != null) {
            when(transactionDao.sumAmountByFamilyPeriodAndType(1L, "2026-01", "EXPENSE"))
                    .thenReturn(new BigDecimal(familyTotalAfter));
        }

        transactionService.create(1L, CREATOR_ID, "user@b.com", "Chủ hộ",
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal(amount), occurredAt, null));

        ArgumentCaptor<ExpenseEvent> captor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(e -> !ExpenseEvent.EXPENSE_CREATED.equals(e.eventType()))
                .toList();
    }

    private static Budget budget(Long categoryId, String limit) {
        Budget budget = new Budget();
        budget.setFamilyId(1L);
        budget.setCategoryId(categoryId);
        budget.setPeriodMonth("2026-01");
        budget.setLimitAmount(new BigDecimal(limit));
        return budget;
    }

    private static Budget overallBudget(String limit) {
        return budget(null, limit);
    }

    @Test
    void create_storesCreatorDisplayNameSnapshotOnTransaction() {
        Transaction saved = createAndCaptureInsertedTransaction("Chủ hộ");

        assertThat(saved.getCreatedByName()).isEqualTo("Chủ hộ");
    }

    @Test
    void create_truncatesCreatorDisplayNameTo100Characters() {
        Transaction saved = createAndCaptureInsertedTransaction("A".repeat(150));

        assertThat(saved.getCreatedByName()).hasSize(100);
    }

    private Transaction createAndCaptureInsertedTransaction(String userDisplayName) {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        Category category = new Category();
        category.setId(7L);
        when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet);
        when(categoryService.requireOwnedByFamily(7L, 1L)).thenReturn(category);

        TransactionResponse response = transactionService.create(1L, CREATOR_ID, "user@b.com", userDisplayName,
                new TransactionRequest(5L, 7L, "INCOME", BigDecimal.TEN, LocalDateTime.of(2026, 1, 15, 10, 0), null));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionDao).insert(captor.capture());
        assertThat(response.createdByName()).isEqualTo(captor.getValue().getCreatedByName());
        return captor.getValue();
    }

    @Test
    void bulkDelete_countsDeletedSkippedAndForbidden_andIgnoresDuplicateIds() {
        Transaction mine = transaction(1L);
        Transaction someoneElses = transaction(2L);
        someoneElses.setUserId(OTHER_USER_ID);
        Transaction otherFamily = transaction(4L);
        otherFamily.setFamilyId(2L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(mine));
        when(transactionDao.selectById(2L)).thenReturn(Optional.of(someoneElses));
        when(transactionDao.selectById(3L)).thenReturn(Optional.empty());
        when(transactionDao.selectById(4L)).thenReturn(Optional.of(otherFamily));

        var result = transactionService.bulkDelete(1L, List.of(1L, 2L, 3L, 4L, 1L), CREATOR_ID, false);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.forbidden()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(mine.getDeletedAt()).isNotNull();
        assertThat(someoneElses.getDeletedAt()).isNull();
        assertThat(otherFamily.getDeletedAt()).isNull();
        verify(transactionDao).update(mine);
        verify(transactionDao, never()).update(someoneElses);
        verify(transactionDao, never()).update(otherFamily);
    }

    @Test
    void bulkDelete_deletesEverything_whenCallerIsOwner() {
        Transaction first = transaction(1L);
        Transaction second = transaction(2L);
        second.setUserId(OTHER_USER_ID);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(first));
        when(transactionDao.selectById(2L)).thenReturn(Optional.of(second));

        var result = transactionService.bulkDelete(1L, List.of(1L, 2L), 99L, true);

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.forbidden()).isZero();
    }

    @Test
    void bulkDelete_throwsBadRequest_whenMoreThan100Ids() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> transactionService.bulkDelete(1L, ids, CREATOR_ID, true))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).selectById(any());
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenMinAmountGreaterThanMaxAmount() {
        TransactionReportFilter filter = new TransactionReportFilter(
                null, null, null, null, null, null, new BigDecimal("500"), new BigDecimal("100"));

        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, filter, 0, 20))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).countByFamilyIdFiltered(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listByFamilyPaged_passesNullPattern_whenSearchTextBlank() {
        TransactionReportFilter filter = new TransactionReportFilter(
                null, null, null, null, null, "   ", null, null);
        when(transactionDao.countByFamilyIdFiltered(1L, null, null, null, null, null, null, null, null))
                .thenReturn(0L);
        when(transactionDao.selectByFamilyIdFiltered(1L, null, null, null, null, null, null, null, null, 10, 0))
                .thenReturn(List.of());

        var page = transactionService.listByFamilyPaged(1L, filter, 0, 10);

        assertThat(page.content()).isEmpty();
    }

    @Test
    void noteLikePattern_escapesWildcardsAndEscapeChar_andLowerCases() {
        TransactionReportFilter filter = new TransactionReportFilter(
                null, null, null, null, null, "  50%_Off! ", null, null);

        assertThat(filter.noteLikePattern()).isEqualTo("%50!%!_off!!%");
    }
}
