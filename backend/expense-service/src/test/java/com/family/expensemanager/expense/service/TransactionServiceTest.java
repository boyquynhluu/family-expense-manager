package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import com.family.expensemanager.expense.dto.TransactionSnapshot;

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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author boyquynhluu
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long CREATOR_ID = 10L;
    private static final Long OTHER_USER_ID = 11L;
    // Real JPEG magic bytes (FF D8 FF) — uploadReceipt now sniffs these instead of trusting the
    // Content-Type header or filename, so fixtures need genuine bytes for the happy-path tests.
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3};

    @Mock
    private TransactionDao transactionDao;
    @Mock
    private BudgetMonitor budgetMonitor;
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
    @Mock
    private IdempotencyGuard idempotencyGuard;
    @Mock
    private TransactionAuditService auditService;
    @Mock
    private PeriodLockService periodLockService;
    @Mock
    private SpendingLimitService spendingLimitService;
    @Mock
    private com.family.expensemanager.expense.dao.TransactionSplitDao transactionSplitDao;
    @Mock
    private com.family.expensemanager.expense.dao.TagDao tagDao;

    private TransactionService transactionService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transactionService = new TransactionService(
                transactionDao, budgetMonitor, walletService, categoryService, eventPublisher, cacheManager,
                receiptStorageService, idempotencyGuard, auditService, periodLockService, spendingLimitService,
                transactionSplitDao, tagDao);
        // None of these tests exercise idempotency (they all pass a null key) — just run the action,
        // like the real IdempotencyGuard does for a null/blank key.
        lenient().when(idempotencyGuard.runOnce(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(4)).get());
    }

    @Test
    void listByFamilyPaged_passesFilterAndOffsetThrough_toTheDao() {
        TransactionReportFilter filter = new TransactionReportFilter(
                5L, 7L, "EXPENSE", null, null, "an sang", new BigDecimal("10"), new BigDecimal("500"));
        when(transactionDao.countByFamilyIdFiltered(
                1L, CREATOR_ID, false, 5L, 7L, "EXPENSE", null, null, "%an sang%", new BigDecimal("10"), new BigDecimal("500"), null)).thenReturn(45L);
        when(transactionDao.selectByFamilyIdFiltered(
                1L, CREATOR_ID, false, 5L, 7L, "EXPENSE", null, null, "%an sang%", new BigDecimal("10"), new BigDecimal("500"), null, 20, 40))
                .thenReturn(List.of(transaction(99L)));

        var page = transactionService.listByFamilyPaged(1L, CREATOR_ID, filter, 2, 20);

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
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, CREATOR_ID, filter, -1, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenSizeOutOfRange() {
        TransactionReportFilter filter = new TransactionReportFilter(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, CREATOR_ID, filter, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, CREATOR_ID, filter, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenSearchTextLongerThan100() {
        TransactionReportFilter ok = new TransactionReportFilter(null, null, null, null, null, "a".repeat(100), null, null);
        TransactionReportFilter tooLong =
                new TransactionReportFilter(null, null, null, null, null, "a".repeat(101), null, null);

        assertThatCode(() -> transactionService.listByFamilyPaged(1L, CREATOR_ID, ok, 0, 20)).doesNotThrowAnyException();
        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, CREATOR_ID, tooLong, 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_savesChanges_whenMemberEditsOwnTransaction() {
        Transaction target = transaction(1L);
        stubUpdateDependencies(target);

        var response = transactionService.update(1L, 1L, CREATOR_ID, "An", false, updateRequest());

        assertThat(response.amount()).isEqualByComparingTo("99000");
        verify(transactionDao).update(target);
    }

    @Test
    void create_rejectsAmountBelowTenThousand_beforeTouchingAnything() {
        assertThatThrownBy(() -> transactionService.create(1L, CREATOR_ID, "user@b.com", "Chủ hộ",
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("9999"), LocalDateTime.now(), null), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Số tiền giao dịch tối thiểu là 10.000đ");
        verify(transactionDao, never()).insert(any(Transaction.class));
    }

    @Test
    void create_rejectsAmountAboveFiveMillion() {
        assertThatThrownBy(() -> transactionService.create(1L, CREATOR_ID, "user@b.com", "Chủ hộ",
                new TransactionRequest(5L, 7L, "INCOME", new BigDecimal("5000000.01"), LocalDateTime.now(), null), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Số tiền giao dịch tối đa là 5.000.000đ");
        verify(transactionDao, never()).insert(any(Transaction.class));
    }

    @Test
    void create_acceptsExactlyTenThousand() {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        Category category = new Category();
        category.setId(7L);
        when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet);
        when(categoryService.requireOwnedByFamily(eq(7L), eq(1L), anyString())).thenReturn(category);

        assertThatCode(() -> transactionService.create(1L, CREATOR_ID, "user@b.com", "Chủ hộ",
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("10000"), LocalDateTime.now(), null), null))
                .doesNotThrowAnyException();
    }

    @Test
    void update_rejectsAnOldOutOfRangeAmount_evenWhenOnlyOtherFieldsChange() {
        Transaction target = transaction(1L); // amount 10, from before the range existed
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transactionService.update(1L, 1L, CREATOR_ID, "An", false,
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("10.00"), LocalDateTime.now(), "sửa ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Số tiền giao dịch tối thiểu là 10.000đ");
        verify(transactionDao, never()).update(any(Transaction.class));
    }

    @Test
    void update_rejectsChangingTheAmountToBelowTenThousand() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transactionService.update(1L, 1L, CREATOR_ID, "An", false,
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("5000"), LocalDateTime.now(), null)))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).update(any(Transaction.class));
    }

    @Test
    void privateTransaction_isHiddenFromOtherMembers_evenTheOwner_butNotFromItsCreator() {
        Transaction secret = transaction(1L);
        secret.setIsPrivate(true);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(secret));

        assertThat(transactionService.get(1L, 1L, CREATOR_ID).isPrivate()).isTrue();
        assertThatThrownBy(() -> transactionService.get(1L, 1L, OTHER_USER_ID)).isInstanceOf(NotFoundException.class);
        // OWNER (callerIsOwner = true) can normally edit/delete anyone's entry — but not one it can't see.
        assertThatThrownBy(() -> transactionService.update(1L, 1L, OTHER_USER_ID, "Chủ hộ", true, updateRequest()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> transactionService.delete(1L, 1L, OTHER_USER_ID, "Chủ hộ", true))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> transactionService.history(1L, 1L, OTHER_USER_ID))
                .isInstanceOf(NotFoundException.class);
        verify(transactionDao, never()).update(any());
    }

    @Test
    void listDeletedPaged_masksOtherMembersPrivateTransactions_butShowsThemInFullToTheCreator() {
        Transaction secret = transaction(1L);
        secret.setIsPrivate(true);
        secret.setNote("Quà sinh nhật");
        secret.setDeletedAt(LocalDateTime.now());
        when(transactionDao.countDeletedByFamilyId(1L)).thenReturn(1L);
        when(transactionDao.selectDeletedByFamilyIdPaged(1L, 5, 0)).thenReturn(List.of(secret));

        var asOther = transactionService.listDeletedPaged(1L, OTHER_USER_ID, 0, 5).content().get(0);
        var asCreator = transactionService.listDeletedPaged(1L, CREATOR_ID, 0, 5).content().get(0);

        assertThat(asOther.isPrivate()).isTrue();
        assertThat(asOther.amount()).isNull();
        assertThat(asOther.note()).isNull();
        assertThat(asOther.type()).isNull();
        assertThat(asOther.occurredAt()).isEqualTo(secret.getOccurredAt()); // the date is public
        assertThat(asOther.walletId()).isNull();
        assertThat(asOther.categoryId()).isNull();
        assertThat(asOther.deletedAt()).isNotNull();
        assertThat(asCreator.note()).isEqualTo("Quà sinh nhật");
        assertThat(asCreator.amount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void listByFamilyPaged_showsOthersPrivateTransactionsMasked_withTheirDate_unlessFilteredByMoreThanDate() {
        Transaction secret = transaction(1L); // created by CREATOR_ID
        secret.setIsPrivate(true);
        secret.setNote("Quà sinh nhật");
        TransactionReportFilter none = new TransactionReportFilter(null, null, null, null, null, null, null, null);
        TransactionReportFilter byNote = new TransactionReportFilter(null, null, null, null, null, "quà", null, null);
        when(transactionDao.countByFamilyIdFiltered(1L, OTHER_USER_ID, true, null, null, null, null, null, null, null, null, null))
                .thenReturn(1L);
        when(transactionDao.selectByFamilyIdFiltered(
                1L, OTHER_USER_ID, true, null, null, null, null, null, null, null, null, null, 10, 0))
                .thenReturn(List.of(secret));

        var row = transactionService.listByFamilyPaged(1L, OTHER_USER_ID, none, 0, 10).content().get(0);
        transactionService.listByFamilyPaged(1L, OTHER_USER_ID, byNote, 0, 10);

        assertThat(row.isPrivate()).isTrue();
        assertThat(row.note()).isNull();
        assertThat(row.amount()).isNull();
        assertThat(row.occurredAt()).isEqualTo(secret.getOccurredAt());
        assertThat(row.walletId()).isNull();
        assertThat(row.createdByName()).isEqualTo(secret.getCreatedByName());
        TransactionReportFilter septemberOnly = new TransactionReportFilter(null, null, null,
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 30), null, null, null);
        transactionService.listByFamilyPaged(1L, OTHER_USER_ID, septemberOnly, 0, 10);
        verify(transactionDao).countByFamilyIdFiltered(eq(1L), eq(OTHER_USER_ID), eq(true), any(), any(), any(),
                eq(java.time.LocalDate.of(2026, 9, 1)), eq(java.time.LocalDate.of(2026, 9, 30)), any(), any(), any(), any());
        // A note search must not let a "***" row through (its match would reveal the note).
        verify(transactionDao).countByFamilyIdFiltered(
                eq(1L), eq(OTHER_USER_ID), eq(false), any(), any(), any(), any(), any(), eq("%quà%"), any(), any(), any());
    }

    @Test
    void locate_returnsThePageHoldingTheTransaction_fromHowManyRowsComeBeforeIt() {
        Transaction backDated = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(backDated));
        TransactionReportFilter none = new TransactionReportFilter(null, null, null, null, null, null, null, null);
        when(transactionDao.countFilteredMatchingId(
                1L, CREATOR_ID, null, null, null, null, null, null, null, null, null, 1L)).thenReturn(1L);
        when(transactionDao.countFilteredAhead(
                1L, CREATOR_ID, true, null, null, null, null, null, null, null, null, null, backDated.getOccurredAt(), 1L))
                .thenReturn(12L); // 12 newer rows → 13th row → page index 2 with 5 per page

        var location = transactionService.locate(1L, CREATOR_ID, none, 1L, 5);

        assertThat(location.inList()).isTrue();
        assertThat(location.page()).isEqualTo(2);
    }

    @Test
    void locate_reportsNotInList_whenTheTransactionDoesNotMatchTheFilters() {
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(transaction(1L)));
        TransactionReportFilter octoberOnly = new TransactionReportFilter(null, null, null,
                java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31), null, null, null);
        when(transactionDao.countFilteredMatchingId(eq(1L), eq(CREATOR_ID), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), eq(1L))).thenReturn(0L);

        var location = transactionService.locate(1L, CREATOR_ID, octoberOnly, 1L, 5);

        assertThat(location.inList()).isFalse();
        verify(transactionDao, never()).countFilteredAhead(any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void delete_publishesAnEventWithoutDetails_whenTheTransactionIsPrivate() {
        Transaction secret = transaction(1L);
        secret.setIsPrivate(true);
        secret.setNote("Quà sinh nhật");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(secret));

        transactionService.delete(1L, 1L, CREATOR_ID, "An", false);

        ArgumentCaptor<ExpenseEvent> captor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ExpenseEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(ExpenseEvent.EXPENSE_DELETED);
        assertThat(event.hidesDetails()).isTrue();
        assertThat(event.amount()).isNull();
        assertThat(event.note()).isNull();
        assertThat(event.occurredOn()).isNull();
        assertThat(event.transactionId()).isNull();
        assertThat(event.categoryId()).isNull();
    }

    @Test
    void update_letsTheCreatorToggleThePrivateFlag_butNotAnOwnerEditingSomeoneElsesEntry() {
        Transaction mine = transaction(1L);
        stubUpdateDependencies(mine);
        TransactionRequest makePrivate =
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("99000"), LocalDateTime.now(), "sửa", true);

        transactionService.update(1L, 1L, CREATOR_ID, "An", false, makePrivate);
        assertThat(mine.getIsPrivate()).isTrue();

        mine.setIsPrivate(false);
        transactionService.update(1L, 1L, OTHER_USER_ID, "Chủ hộ", true, makePrivate);
        assertThat(mine.getIsPrivate()).isFalse();
    }

    @Test
    void update_doesNotRecheckWalletOwnership_whenWalletUnchanged() {
        Transaction target = transaction(1L); // already in wallet 5, request keeps wallet 5
        stubUpdateDependencies(target);

        transactionService.update(1L, 1L, CREATOR_ID, "An", false, updateRequest());

        verify(walletService, never()).requireUsableBy(any(Wallet.class), any(), anyBoolean());
    }

    @Test
    void update_checksWalletOwnership_whenMovingToAnotherWallet() {
        Transaction target = transaction(1L);
        target.setWalletId(3L); // request moves it to wallet 5
        stubUpdateDependencies(target);

        transactionService.update(1L, 1L, CREATOR_ID, "An", false, updateRequest());

        verify(walletService).requireUsableBy(any(Wallet.class), eq(CREATOR_ID), eq(false));
    }

    @Test
    void create_checksWalletOwnership_withTheCallersRole() {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet);
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "ví riêng của thành viên khác"))
                .when(walletService).requireUsableBy(wallet, OTHER_USER_ID, false);

        assertForbidden(() -> transactionService.create(1L, OTHER_USER_ID, "m@b.com", "Member", false,
                new TransactionRequest(5L, 7L, "INCOME", BigDecimal.valueOf(50000), LocalDateTime.now(), null), null));
        verify(transactionDao, never()).insert(any());
    }

    @Test
    void update_logsTheStateBeforeAndAfterTheEdit() {
        Transaction target = transaction(1L);
        stubUpdateDependencies(target);

        transactionService.update(1L, 1L, CREATOR_ID, "An", false, updateRequest());

        ArgumentCaptor<TransactionSnapshot> before = ArgumentCaptor.forClass(TransactionSnapshot.class);
        ArgumentCaptor<TransactionSnapshot> after = ArgumentCaptor.forClass(TransactionSnapshot.class);
        verify(auditService).record(eq(TransactionAuditService.ACTION_UPDATED), eq(1L), eq(1L), before.capture(),
                after.capture(), eq(CREATOR_ID), eq("An"));
        assertThat(before.getValue().amount()).isEqualByComparingTo("10");
        assertThat(after.getValue().amount()).isEqualByComparingTo("99000");
    }

    @Test
    void update_throwsForbidden_whenMemberEditsSomeoneElsesTransaction() {
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(transaction(1L)));

        assertForbidden(() -> transactionService.update(1L, 1L, OTHER_USER_ID, "An", false, updateRequest()));
        verify(transactionDao, never()).update(any());
    }

    @Test
    void update_succeeds_whenOwnerEditsSomeoneElsesTransaction() {
        Transaction target = transaction(1L);
        stubUpdateDependencies(target);

        transactionService.update(1L, 1L, OTHER_USER_ID, "An", true, updateRequest());

        verify(transactionDao).update(target);
    }

    @Test
    void uploadReceipt_savesFile_andUpdatesTransaction() throws Exception {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(receiptStorageService.save(eq(1L), eq(1L), any(), any())).thenReturn("1/1-123.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", JPEG_BYTES);

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
        when(receiptStorageService.save(eq(1L), eq(1L), any(), any())).thenReturn("1/1-new.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", JPEG_BYTES);

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

    // Regression test: the Content-Type header and filename are attacker-controlled — a request could
    // claim "image/jpeg" for bytes that aren't actually a JPEG. uploadReceipt must sniff the real bytes
    // and reject this, not just check the header.
    @Test
    void uploadReceipt_throwsBadRequest_whenContentTypeHeaderIsSpoofed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hoadon.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> transactionService.uploadReceipt(1L, 1L, CREATOR_ID, false, file))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).selectById(any());
        verify(receiptStorageService, never()).save(any(), any(), any(), any());
    }

    @Test
    void uploadReceipt_throwsForbidden_whenMemberAttachesToSomeoneElsesTransaction() throws Exception {
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(transaction(1L)));
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", JPEG_BYTES);

        assertForbidden(() -> transactionService.uploadReceipt(1L, 1L, OTHER_USER_ID, false, file));
        verify(receiptStorageService, never()).save(any(), any(), any(), any());
        verify(transactionDao, never()).update(any());
    }

    @Test
    void uploadReceipt_succeeds_whenOwnerAttachesToSomeoneElsesTransaction() throws Exception {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(receiptStorageService.save(eq(1L), eq(1L), any(), any())).thenReturn("1/1-123.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", JPEG_BYTES);

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

        var receipt = transactionService.getReceipt(1L, 1L, CREATOR_ID);

        assertThat(receipt.content()).containsExactly(9, 9);
        assertThat(receipt.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void getReceipt_throwsNotFound_whenNoReceiptAttached() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transactionService.getReceipt(1L, 1L, CREATOR_ID)).isInstanceOf(NotFoundException.class);
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

        transactionService.delete(1L, 1L, CREATOR_ID, "An", false);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(transactionDao).update(target);
        verify(transactionDao, never()).delete(any());
        verify(receiptStorageService, never()).delete(any());
    }

    @Test
    void delete_recordsWhoDeletedIt_logsTheOldState_andNotifiesTheFamily() {
        Transaction target = transaction(1L);
        target.setNote("Tiền chợ");
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.delete(1L, 1L, CREATOR_ID, "An", false);

        // Level 1 — who, not just when.
        assertThat(target.getDeletedByUserId()).isEqualTo(CREATOR_ID);
        assertThat(target.getDeletedByName()).isEqualTo("An");
        // Level 3 — the row as it was, and nothing after.
        ArgumentCaptor<TransactionSnapshot> before = ArgumentCaptor.forClass(TransactionSnapshot.class);
        verify(auditService).record(eq(TransactionAuditService.ACTION_DELETED), eq(1L), eq(1L), before.capture(),
                isNull(), eq(CREATOR_ID), eq("An"));
        assertThat(before.getValue().amount()).isEqualByComparingTo("10");
        assertThat(before.getValue().note()).isEqualTo("Tiền chợ");
        // Level 2 — one notification describing this transaction.
        ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ExpenseEvent.EXPENSE_DELETED);
        assertThat(event.getValue().transactionId()).isEqualTo(1L);
        assertThat(event.getValue().userDisplayName()).isEqualTo("An");
        assertThat(event.getValue().note()).isEqualTo("Tiền chợ");
        assertThat(event.getValue().itemCount()).isEqualTo(1);
    }

    @Test
    void delete_throwsForbidden_whenMemberDeletesSomeoneElsesTransaction() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        assertForbidden(() -> transactionService.delete(1L, 1L, OTHER_USER_ID, "An", false));
        assertThat(target.getDeletedAt()).isNull();
        verify(transactionDao, never()).update(any());
    }

    @Test
    void delete_softDeletes_whenOwnerDeletesSomeoneElsesTransaction() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));

        transactionService.delete(1L, 1L, OTHER_USER_ID, "An", true);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(transactionDao).update(target);
    }

    @Test
    void restore_clearsDeletedAt_whenMemberRestoresOwnTransaction() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(transaction(1L)));
        when(transactionDao.restore(1L, 1L)).thenReturn(1);

        transactionService.restore(1L, 1L, CREATOR_ID, "An", false);

        verify(transactionDao).restore(1L, 1L);
    }

    @Test
    void restore_logsARestoredEntry() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(transaction(1L)));
        when(transactionDao.restore(1L, 1L)).thenReturn(1);

        transactionService.restore(1L, 1L, CREATOR_ID, "An", false);

        verify(auditService).record(eq(TransactionAuditService.ACTION_RESTORED), eq(1L), eq(1L), isNull(),
                any(TransactionSnapshot.class), eq(CREATOR_ID), eq("An"));
    }

    @Test
    void restore_throwsNotFound_whenRowMissingOrNotDeleted() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.restore(1L, 1L, CREATOR_ID, "An", false))
                .isInstanceOf(NotFoundException.class);
        verify(transactionDao, never()).restore(any(), any());
    }

    @Test
    void restore_throwsNotFound_whenDeletedRowBelongsToAnotherFamily() {
        Transaction deleted = transaction(1L);
        deleted.setFamilyId(2L);
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> transactionService.restore(1L, 1L, CREATOR_ID, "An", true))
                .isInstanceOf(NotFoundException.class);
        verify(transactionDao, never()).restore(any(), any());
    }

    @Test
    void restore_throwsForbidden_whenMemberRestoresSomeoneElsesTransaction() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(transaction(1L)));

        assertForbidden(() -> transactionService.restore(1L, 1L, OTHER_USER_ID, "An", false));
        verify(transactionDao, never()).restore(any(), any());
    }

    @Test
    void restore_succeeds_whenOwnerRestoresSomeoneElsesTransaction() {
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(transaction(1L)));
        when(transactionDao.restore(1L, 1L)).thenReturn(1);

        transactionService.restore(1L, 1L, OTHER_USER_ID, "An", true);

        verify(transactionDao).restore(1L, 1L);
    }

    @Test
    void listDeletedPaged_returnsPageWithOffset() {
        when(transactionDao.countDeletedByFamilyId(1L)).thenReturn(12L);
        when(transactionDao.selectDeletedByFamilyIdPaged(1L, 5, 10))
                .thenReturn(List.of(transaction(98L), transaction(99L)));

        var result = transactionService.listDeletedPaged(1L, CREATOR_ID, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(1).id()).isEqualTo(99L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listDeletedPaged_rejectsNegativePage() {
        assertThatThrownBy(() -> transactionService.listDeletedPaged(1L, CREATOR_ID, -1, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listDeletedPaged_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> transactionService.listDeletedPaged(1L, CREATOR_ID, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transactionService.listDeletedPaged(1L, CREATOR_ID, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    private void stubUpdateDependencies(Transaction target) {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        Category category = new Category();
        category.setId(7L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet);
        when(categoryService.requireOwnedByFamily(eq(7L), eq(1L), anyString())).thenReturn(category);
    }

    private static TransactionRequest updateRequest() {
        return new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("99000"), LocalDateTime.now(), "sửa");
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
    void create_handsAnExpenseToTheBudgetMonitor_withOneLineForItsCategory() {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        Category category = new Category();
        category.setId(7L);
        when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet);
        when(categoryService.requireOwnedByFamily(eq(7L), eq(1L), anyString())).thenReturn(category);

        transactionService.create(1L, CREATOR_ID, "user@b.com", "Chủ hộ",
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("100000"), LocalDateTime.of(2026, 1, 15, 10, 0),
                        null), null);

        verify(budgetMonitor).onExpenseRecorded(eq(1L), eq(CREATOR_ID), eq("user@b.com"), eq("Chủ hộ"),
                any(Transaction.class), eq(List.of(new BudgetMonitor.Line(7L, new BigDecimal("100000")))));
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
        when(categoryService.requireOwnedByFamily(eq(7L), eq(1L), anyString())).thenReturn(category);

        TransactionResponse response = transactionService.create(1L, CREATOR_ID, "user@b.com", userDisplayName,
                new TransactionRequest(5L, 7L, "INCOME", BigDecimal.valueOf(50000), LocalDateTime.of(2026, 1, 15, 10, 0), null), null);

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

        var result = transactionService.bulkDelete(1L, List.of(1L, 2L, 3L, 4L, 1L), CREATOR_ID, "An", false);

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

        var result = transactionService.bulkDelete(1L, List.of(1L, 2L), 99L, "An", true);

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.forbidden()).isZero();
    }

    @Test
    void bulkDelete_publishesOneNotificationForTheWholeBatch_butLogsEachRow() {
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(transaction(1L)));
        when(transactionDao.selectById(2L)).thenReturn(Optional.of(transaction(2L)));
        when(transactionDao.selectById(3L)).thenReturn(Optional.of(transaction(3L)));

        transactionService.bulkDelete(1L, List.of(1L, 2L, 3L), CREATOR_ID, "An", false);

        ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ExpenseEvent.EXPENSE_DELETED);
        assertThat(event.getValue().itemCount()).isEqualTo(3);
        assertThat(event.getValue().transactionId()).isNull();
        verify(auditService, times(3)).record(eq(TransactionAuditService.ACTION_DELETED), eq(1L), any(), any(),
                isNull(), eq(CREATOR_ID), eq("An"));
    }

    @Test
    void bulkDelete_publishesNothing_whenNothingWasDeleted() {
        when(transactionDao.selectById(1L)).thenReturn(Optional.empty());

        transactionService.bulkDelete(1L, List.of(1L), CREATOR_ID, "An", false);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void bulkDelete_throwsBadRequest_whenMoreThan100Ids() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> transactionService.bulkDelete(1L, ids, CREATOR_ID, "An", true))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).selectById(any());
    }

    @Test
    void listByFamilyPaged_throwsBadRequest_whenMinAmountGreaterThanMaxAmount() {
        TransactionReportFilter filter = new TransactionReportFilter(
                null, null, null, null, null, null, new BigDecimal("500"), new BigDecimal("100"));

        assertThatThrownBy(() -> transactionService.listByFamilyPaged(1L, CREATOR_ID, filter, 0, 20))
                .isInstanceOf(BadRequestException.class);
        verify(transactionDao, never()).countByFamilyIdFiltered(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listByFamilyPaged_passesNullPattern_whenSearchTextBlank() {
        TransactionReportFilter filter = new TransactionReportFilter(
                null, null, null, null, null, "   ", null, null);
        when(transactionDao.countByFamilyIdFiltered(1L, CREATOR_ID, true, null, null, null, null, null, null, null, null, null))
                .thenReturn(0L);
        when(transactionDao.selectByFamilyIdFiltered(1L, CREATOR_ID, true, null, null, null, null, null, null, null, null, null, 10, 0))
                .thenReturn(List.of());

        var page = transactionService.listByFamilyPaged(1L, CREATOR_ID, filter, 0, 10);

        assertThat(page.content()).isEmpty();
    }

    @Test
    void noteLikePattern_escapesWildcardsAndEscapeChar_andLowerCases() {
        TransactionReportFilter filter = new TransactionReportFilter(
                null, null, null, null, null, "  50%_Off! ", null, null);

        assertThat(filter.noteLikePattern()).isEqualTo("%50!%!_off!!%");
    }

    // ===== README B2: closed months (PeriodLockService) =====

    private static ConflictException monthClosed() {
        return new ConflictException("Tháng 2026-08 đã chốt sổ");
    }

    @Test
    void create_isRefused_whenItsMonthIsClosed_beforeAnythingIsWritten() {
        LocalDateTime inClosedMonth = LocalDateTime.of(2026, 8, 10, 9, 0);
        doThrow(monthClosed()).when(periodLockService).requireUnlocked(1L, inClosedMonth);

        assertThatThrownBy(() -> transactionService.create(1L, CREATOR_ID, "user@b.com", "An",
                new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("50000"), inClosedMonth, null), null))
                .isInstanceOf(ConflictException.class);
        verify(transactionDao, never()).insert(any(Transaction.class));
    }

    @Test
    void update_checksBothTheOldAndTheNewDate_soNothingMovesIntoOrOutOfAClosedMonth() {
        Transaction target = transaction(1L);
        LocalDateTime oldDate = target.getOccurredAt();
        stubUpdateDependencies(target);
        TransactionRequest request = updateRequest();

        transactionService.update(1L, 1L, CREATOR_ID, "An", false, request);

        verify(periodLockService).requireUnlocked(1L, oldDate, request.occurredAt());
    }

    @Test
    void update_isRefused_andNothingSaved_whenAMonthInvolvedIsClosed() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        doThrow(monthClosed()).when(periodLockService).requireUnlocked(eq(1L), any(), any());

        assertThatThrownBy(() -> transactionService.update(1L, 1L, CREATOR_ID, "An", false, updateRequest()))
                .isInstanceOf(ConflictException.class);
        verify(transactionDao, never()).update(any(Transaction.class));
    }

    @Test
    void delete_isRefused_whenTheTransactionsMonthIsClosed() {
        Transaction target = transaction(1L);
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(target));
        doThrow(monthClosed()).when(periodLockService).requireUnlocked(1L, target.getOccurredAt());

        assertThatThrownBy(() -> transactionService.delete(1L, 1L, CREATOR_ID, "An", false))
                .isInstanceOf(ConflictException.class);
        assertThat(target.getDeletedAt()).isNull();
        verify(transactionDao, never()).update(any(Transaction.class));
    }

    @Test
    void bulkDelete_countsRowsOfAClosedMonthAsLocked_andDeletesTheRest() {
        Transaction open = transaction(1L);
        Transaction closed = transaction(2L);
        closed.setOccurredAt(LocalDateTime.of(2026, 8, 10, 9, 0));
        when(transactionDao.selectById(1L)).thenReturn(Optional.of(open));
        when(transactionDao.selectById(2L)).thenReturn(Optional.of(closed));
        // lenient: the open row's date reaches the same mock with other arguments (and must pass).
        lenient().doThrow(monthClosed()).when(periodLockService).requireUnlocked(1L, closed.getOccurredAt());

        var result = transactionService.bulkDelete(1L, List.of(1L, 2L), CREATOR_ID, "An", false);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.locked()).isEqualTo(1);
        assertThat(closed.getDeletedAt()).isNull();
        verify(transactionDao, never()).update(closed);
    }

    @Test
    void restore_isRefused_whenTheTransactionsMonthIsClosed() {
        Transaction deleted = transaction(1L);
        deleted.setDeletedAt(LocalDateTime.now());
        when(transactionDao.selectDeletedById(1L)).thenReturn(Optional.of(deleted));
        doThrow(monthClosed()).when(periodLockService).requireUnlocked(1L, deleted.getOccurredAt());

        assertThatThrownBy(() -> transactionService.restore(1L, 1L, CREATOR_ID, "An", false))
                .isInstanceOf(ConflictException.class);
        verify(transactionDao, never()).restore(any(), any());
    }
}
