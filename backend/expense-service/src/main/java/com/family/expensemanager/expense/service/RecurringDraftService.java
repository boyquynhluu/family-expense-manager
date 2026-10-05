package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.RecurringDraftDao;
import com.family.expensemanager.expense.domain.entity.RecurringDraft;
import com.family.expensemanager.expense.dto.ConfirmDraftRequest;
import com.family.expensemanager.expense.dto.RecurringDraftResponse;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * README A4 "nhắc và chờ xác nhận": drafts created by a CONFIRM-mode recurring rule. Confirming records a normal
 * transaction (every usual check applies: amount range, closed months, wallet balance, budgets) with the real
 * amount typed by the member; skipping just closes the occurrence. The rule's creator or the OWNER may decide.
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "RecurringDraftService")
public class RecurringDraftService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_CONFIRMED = "CONFIRMED";
    static final String STATUS_SKIPPED = "SKIPPED";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;

    private final RecurringDraftDao recurringDraftDao;
    private final TransactionService transactionService;
    private final Clock clock;

    public PageResponse<RecurringDraftResponse> listPending(Long familyId, int page, int size) {
        try {
            log.info("listPending - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long total = recurringDraftDao.countPendingByFamilyId(familyId);
            List<RecurringDraftResponse> content = recurringDraftDao.selectPendingByFamilyIdPaged(familyId, size, page * size)
                    .stream().map(RecurringDraftResponse::from).toList();
            return PageResponse.of(content, page, size, total);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("RecurringDraftService.listPending", e);
        }
    }

    public long countPending(Long familyId) {
        return recurringDraftDao.countPendingByFamilyId(familyId);
    }

    public RecurringDraftResponse confirm(Long familyId, Long draftId, Long userId, String userEmail, String userName,
                                          boolean callerIsOwner, ConfirmDraftRequest request) {
        try {
            log.info("confirm - start, familyId={}, draftId={}", familyId, draftId);
            RecurringDraft draft = requirePending(familyId, draftId, userId, callerIsOwner);
            String note = request.note() != null && !request.note().isBlank() ? request.note() : draft.getNote();
            TransactionResponse created = transactionService.create(familyId, userId, userEmail, userName, callerIsOwner,
                    new TransactionRequest(draft.getWalletId(), draft.getCategoryId(), draft.getType(), request.amount(),
                            draft.getDueDate().atTime(12, 0), note), null);
            decide(draft, STATUS_CONFIRMED, created.id(), userId, userName);
            return RecurringDraftResponse.from(draft);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("RecurringDraftService.confirm", e);
        }
    }

    public RecurringDraftResponse skip(Long familyId, Long draftId, Long userId, String userName, boolean callerIsOwner) {
        try {
            log.info("skip - start, familyId={}, draftId={}", familyId, draftId);
            RecurringDraft draft = requirePending(familyId, draftId, userId, callerIsOwner);
            decide(draft, STATUS_SKIPPED, null, userId, userName);
            return RecurringDraftResponse.from(draft);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("RecurringDraftService.skip", e);
        }
    }

    private RecurringDraft requirePending(Long familyId, Long draftId, Long userId, boolean callerIsOwner) {
        RecurringDraft draft = recurringDraftDao.selectById(draftId)
                .filter(d -> d.getFamilyId().equals(familyId))
                .orElseThrow(() -> logged(log, new NotFoundException("Khoản chờ xác nhận không tồn tại: " + draftId)));
        if (!STATUS_PENDING.equals(draft.getStatus())) {
            throw logged(log, new ConflictException("Khoản này đã được xử lý"));
        }
        if (!callerIsOwner && !Objects.equals(draft.getCreatedByUserId(), userId)) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Chỉ người tạo giao dịch định kỳ hoặc chủ hộ mới được xác nhận khoản này"));
        }
        return draft;
    }

    private void decide(RecurringDraft draft, String status, Long transactionId, Long userId, String userName) {
        draft.setStatus(status);
        draft.setTransactionId(transactionId);
        draft.setDecidedByUserId(userId);
        draft.setDecidedByName(userName != null && userName.length() > MAX_NAME_LENGTH
                ? userName.substring(0, MAX_NAME_LENGTH) : userName);
        draft.setDecidedAt(LocalDateTime.now(clock));
        recurringDraftDao.update(draft);
    }
}
