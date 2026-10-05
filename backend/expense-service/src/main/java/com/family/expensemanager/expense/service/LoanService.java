package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.currency.CurrencyUtil;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.LoanDao;
import com.family.expensemanager.expense.dao.LoanPaymentDao;
import com.family.expensemanager.expense.domain.entity.Loan;
import com.family.expensemanager.expense.domain.entity.LoanPayment;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.LoanPaymentRequest;
import com.family.expensemanager.expense.dto.LoanRequest;
import com.family.expensemanager.expense.dto.LoanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * README B3 "Vay / cho vay / nợ". A loan and its repayments only move wallet balances (see
 * {@link WalletService#currentBalanceOf}) — never income or expense, budgets or category reports. Money leaving a
 * wallet (lending, repaying a debt) follows the negative-balance policy (A1); dates follow closed months (B2).
 * Anyone may record a loan on a wallet they can use; its creator or the OWNER may change or delete it.
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "LoanService")
public class LoanService {

    static final String BORROWED = "BORROWED";
    static final String LENT = "LENT";
    static final String OPEN = "OPEN";
    static final String CLOSED = "CLOSED";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;

    private final LoanDao loanDao;
    private final LoanPaymentDao loanPaymentDao;
    private final WalletService walletService;
    private final PeriodLockService periodLockService;
    private final Clock clock;

    public LoanResponse create(Long familyId, Long userId, String userName, boolean callerIsOwner, LoanRequest request) {
        try {
            log.info("create - start, familyId={}, direction={}", familyId, request.direction());
            requireDueAfterStart(request);
            periodLockService.requireUnlocked(familyId, request.startDate().atStartOfDay());
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            walletService.requireUsableBy(wallet, userId, callerIsOwner);
            if (LENT.equals(request.direction())) {
                walletService.requireAllowedOutflow(wallet, request.principal());
            }
            Long memberUserId = memberOf(request, userId, callerIsOwner);
            Wallet other = counterpartyWallet(request, wallet, memberUserId, familyId);
            if (other != null && BORROWED.equals(request.direction())) {
                // Borrowing from a family wallet takes the money out of it: the caller must be allowed to use it.
                walletService.requireUsableBy(other, userId, callerIsOwner);
                walletService.requireAllowedOutflow(other, request.principal());
            }
            Loan loan = new Loan();
            loan.setFamilyId(familyId);
            loan.setMemberUserId(memberUserId);
            loan.setDirection(request.direction());
            loan.setCounterpartyWalletId(other == null ? null : other.getId());
            loan.setCounterpartyName(other == null ? requireName(request) : other.getName());
            loan.setCounterpartyContact(request.counterpartyContact());
            loan.setPrincipal(request.principal());
            loan.setWalletId(wallet.getId());
            loan.setStartDate(request.startDate());
            loan.setDueDate(request.dueDate());
            loan.setNote(request.note());
            loan.setStatus(OPEN);
            loan.setCreatedByUserId(userId);
            loan.setCreatedByName(truncate(userName));
            loan.setCreatedAt(LocalDateTime.now(clock));
            loanDao.insert(loan);
            return LoanResponse.from(loan, BigDecimal.ZERO, List.of());
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("LoanService.create", e);
        }
    }

    /** {@code status} OPEN / CLOSED, {@code memberUserId} one member — null for all; repayments only via {@link #get}. */
    public PageResponse<LoanResponse> list(Long familyId, Long memberUserId, String status, int page, int size) {
        try {
            log.info("list - start, familyId={}, status={}", familyId, status);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long total = loanDao.countByFamily(familyId, memberUserId, status);
            List<LoanResponse> content = loanDao.selectByFamilyPaged(familyId, memberUserId, status, size, page * size).stream()
                    .map(l -> LoanResponse.from(l, loanPaymentDao.sumByLoanId(l.getId()), null))
                    .toList();
            return PageResponse.of(content, page, size, total);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("LoanService.list", e);
        }
    }

    public LoanResponse get(Long familyId, Long loanId) {
        Loan loan = requireOwnedByFamily(familyId, loanId);
        return LoanResponse.from(loan, loanPaymentDao.sumByLoanId(loanId), loanPaymentDao.selectByLoanId(loanId));
    }

    /** Only the counterparty, contact, due date and note — the amounts and wallet are history. */
    public LoanResponse update(Long familyId, Long loanId, Long userId, boolean callerIsOwner, LoanRequest request) {
        try {
            log.info("update - start, familyId={}, loanId={}", familyId, loanId);
            Loan loan = requireOwnedByFamily(familyId, loanId);
            requireCanModify(loan.getCreatedByUserId(), userId, callerIsOwner);
            if (request.memberUserId() != null) {
                loan.setMemberUserId(memberOf(request, userId, callerIsOwner));
            }
            if (loan.getCounterpartyWalletId() == null) {
                loan.setCounterpartyName(requireName(request));
            }
            loan.setCounterpartyContact(request.counterpartyContact());
            if (request.dueDate() != null && request.dueDate().isBefore(loan.getStartDate())) {
                throw logged(log, new BadRequestException("Hạn trả không được trước ngày vay"));
            }
            if (!Objects.equals(loan.getDueDate(), request.dueDate())) {
                loan.setLastRemindedFor(null);
            }
            loan.setDueDate(request.dueDate());
            loan.setNote(request.note());
            loanDao.update(loan);
            return get(familyId, loanId);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("LoanService.update", e);
        }
    }

    /** Only a loan without repayments (delete those first), so no balance history silently disappears. */
    public void delete(Long familyId, Long loanId, Long userId, boolean callerIsOwner) {
        try {
            log.info("delete - start, familyId={}, loanId={}", familyId, loanId);
            Loan loan = requireOwnedByFamily(familyId, loanId);
            requireCanModify(loan.getCreatedByUserId(), userId, callerIsOwner);
            if (!loanPaymentDao.selectByLoanId(loanId).isEmpty()) {
                throw logged(log, new ConflictException("Khoản vay đã có lần trả — hãy xoá các lần trả trước"));
            }
            periodLockService.requireUnlocked(familyId, loan.getStartDate().atStartOfDay());
            if (BORROWED.equals(loan.getDirection())) {
                // Removing borrowed money takes it back out of the wallet.
                walletService.requireAllowedOutflow(walletService.requireOwnedByFamily(loan.getWalletId(), familyId),
                        loan.getPrincipal());
            } else if (loan.getCounterpartyWalletId() != null) {
                // Undoing a loan to a family wallet takes the money back out of it.
                walletService.requireAllowedOutflow(
                        walletService.requireOwnedByFamily(loan.getCounterpartyWalletId(), familyId), loan.getPrincipal());
            }
            loanDao.delete(loan);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("LoanService.delete", e);
        }
    }

    public LoanResponse addPayment(Long familyId, Long loanId, Long userId, String userName, boolean callerIsOwner,
                                   LoanPaymentRequest request) {
        try {
            log.info("addPayment - start, familyId={}, loanId={}", familyId, loanId);
            Loan loan = requireOwnedByFamily(familyId, loanId);
            if (CLOSED.equals(loan.getStatus())) {
                throw logged(log, new ConflictException("Khoản vay đã trả xong"));
            }
            BigDecimal remaining = loan.getPrincipal().subtract(loanPaymentDao.sumByLoanId(loanId));
            if (request.amount().compareTo(remaining) > 0) {
                throw logged(log, new BadRequestException("Số tiền trả vượt số còn nợ: " + CurrencyUtil.formatCurrency(remaining)));
            }
            periodLockService.requireUnlocked(familyId, request.paidAt());
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            walletService.requireUsableBy(wallet, userId, callerIsOwner);
            if (BORROWED.equals(loan.getDirection())) {
                walletService.requireAllowedOutflow(wallet, request.amount());
            } else if (loan.getCounterpartyWalletId() != null) {
                Wallet other = walletService.requireOwnedByFamily(loan.getCounterpartyWalletId(), familyId);
                walletService.requireUsableBy(other, userId, callerIsOwner);
                walletService.requireAllowedOutflow(other, request.amount());
            }
            if (wallet.getId().equals(loan.getCounterpartyWalletId())) {
                throw logged(log, new BadRequestException("Không trả nợ bằng chính ví của bên kia"));
            }
            LoanPayment payment = new LoanPayment();
            payment.setLoanId(loanId);
            payment.setFamilyId(familyId);
            payment.setWalletId(wallet.getId());
            payment.setAmount(request.amount());
            payment.setPaidAt(request.paidAt());
            payment.setNote(request.note());
            payment.setCreatedByUserId(userId);
            payment.setCreatedByName(truncate(userName));
            payment.setCreatedAt(LocalDateTime.now(clock));
            loanPaymentDao.insert(payment);
            if (request.amount().compareTo(remaining) == 0) {
                loan.setStatus(CLOSED);
                loanDao.update(loan);
            }
            return get(familyId, loanId);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("LoanService.addPayment", e);
        }
    }

    public LoanResponse deletePayment(Long familyId, Long loanId, Long paymentId, Long userId, boolean callerIsOwner) {
        try {
            log.info("deletePayment - start, familyId={}, loanId={}, paymentId={}", familyId, loanId, paymentId);
            Loan loan = requireOwnedByFamily(familyId, loanId);
            LoanPayment payment = loanPaymentDao.selectById(paymentId)
                    .filter(p -> p.getLoanId().equals(loanId))
                    .orElseThrow(() -> logged(log, new NotFoundException("Lần trả không tồn tại: " + paymentId)));
            requireCanModify(payment.getCreatedByUserId(), userId, callerIsOwner);
            periodLockService.requireUnlocked(familyId, payment.getPaidAt());
            if (LENT.equals(loan.getDirection())) {
                // Undoing money that came back takes it out of the wallet again.
                walletService.requireAllowedOutflow(walletService.requireOwnedByFamily(payment.getWalletId(), familyId),
                        payment.getAmount());
            } else if (loan.getCounterpartyWalletId() != null) {
                // Undoing a repayment into a family wallet takes it back out of that wallet.
                walletService.requireAllowedOutflow(
                        walletService.requireOwnedByFamily(loan.getCounterpartyWalletId(), familyId), payment.getAmount());
            }
            loanPaymentDao.delete(payment);
            if (CLOSED.equals(loan.getStatus())) {
                loan.setStatus(OPEN);
                loanDao.update(loan);
            }
            return get(familyId, loanId);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("LoanService.deletePayment", e);
        }
    }

    /**
     * A loan inside the family: the other side's wallet — any family wallet except the loan's own and the member's
     * own private wallets (borrowing from yourself is not a loan). Null for an outside counterparty.
     */
    private Wallet counterpartyWallet(LoanRequest request, Wallet wallet, Long memberUserId, Long familyId) {
        if (request.counterpartyWalletId() == null) {
            return null;
        }
        Wallet other = walletService.requireOwnedByFamily(request.counterpartyWalletId(), familyId);
        if (other.getId().equals(wallet.getId())) {
            throw logged(log, new BadRequestException("Ví của bên kia phải khác ví của khoản vay"));
        }
        if (memberUserId.equals(other.getOwnerUserId())) {
            throw logged(log, new BadRequestException("Không vay / cho vay giữa các ví của cùng một người"));
        }
        return other;
    }

    private String requireName(LoanRequest request) {
        if (request.counterpartyName() == null || request.counterpartyName().isBlank()) {
            throw logged(log, new BadRequestException("Hãy nhập tên người cho vay / người vay, hoặc chọn một ví trong gia đình"));
        }
        return request.counterpartyName().trim();
    }

    /** The caller themself unless the OWNER names another member. */
    private Long memberOf(LoanRequest request, Long userId, boolean callerIsOwner) {
        if (request.memberUserId() == null || request.memberUserId().equals(userId)) {
            return userId;
        }
        if (!callerIsOwner) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Chỉ chủ hộ mới được ghi khoản vay đứng tên thành viên khác"));
        }
        return request.memberUserId();
    }

    private Loan requireOwnedByFamily(Long familyId, Long loanId) {
        return loanDao.selectById(loanId)
                .filter(l -> l.getFamilyId().equals(familyId))
                .orElseThrow(() -> logged(log, new NotFoundException("Khoản vay không tồn tại: " + loanId)));
    }

    private void requireCanModify(Long createdBy, Long userId, boolean callerIsOwner) {
        if (!callerIsOwner && !Objects.equals(createdBy, userId)) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Chỉ người tạo hoặc chủ hộ mới được thực hiện thao tác này"));
        }
    }

    private void requireDueAfterStart(LoanRequest request) {
        if (request.dueDate() != null && request.dueDate().isBefore(request.startDate())) {
            throw logged(log, new BadRequestException("Hạn trả không được trước ngày vay"));
        }
    }

    private static String truncate(String name) {
        return name != null && name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
