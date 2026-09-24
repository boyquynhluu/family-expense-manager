package com.family.expensemanager.expense.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.domain.entity.WalletTransfer;
import com.family.expensemanager.expense.dto.CreateWalletTransferRequest;
import com.family.expensemanager.expense.dto.WalletTransferResponse;
import java.io.UncheckedIOException;
import org.springframework.security.core.AuthenticationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * Moves money between two wallets of the same family. A transfer is neither income nor expense, so it
 * lives in its own table and only affects wallet balances (see {@link WalletService}), never the
 * transaction lists, reports or budgets.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "WalletTransferService")
public class WalletTransferService {

    private static final String ROLE_OWNER = "OWNER";
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_PAGE_SIZE = 100;

    private final WalletTransferDao walletTransferDao;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    public WalletTransferResponse create(
            Long familyId, Long userId, String userEmail, String userDisplayName, CreateWalletTransferRequest request) {
        try {
            log.info("create - start, familyId={}, from={}, to={}", familyId, request.fromWalletId(), request.toWalletId());
            Wallet[] wallets = requireValidWallets(familyId, request, null);

            WalletTransfer transfer = new WalletTransfer();
            transfer.setFamilyId(familyId);
            transfer.setFromWalletId(wallets[0].getId());
            transfer.setToWalletId(wallets[1].getId());
            transfer.setAmount(request.amount());
            transfer.setNote(request.note());
            transfer.setOccurredAt(request.occurredAt());
            transfer.setCreatedByUserId(userId);
            transfer.setCreatedAt(LocalDateTime.now());
            walletTransferDao.insert(transfer);

            eventPublisher.publishEvent(new ExpenseEvent(
                    ExpenseEvent.WALLET_TRANSFERRED, familyId, userId, null, null, request.amount(), null, null, null,
                    null, userEmail, userDisplayName, Instant.now(), request.occurredAt().toLocalDate(), request.note(),
                    wallets[0].getName(), wallets[1].getName()));

            return WalletTransferResponse.from(transfer);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletTransferService.create", e);
        }
    }

    public WalletTransferResponse update(
            Long familyId, Long userId, String role, Long transferId, CreateWalletTransferRequest request) {
        try {
            log.info("update - start, familyId={}, userId={}, transferId={}", familyId, userId, transferId);
            WalletTransfer transfer = requireOwnedByFamily(transferId, familyId);
            requireCreatorOrOwner(transfer, userId, role, "sửa");
            Wallet[] wallets = requireValidWallets(familyId, request, transfer);

            transfer.setFromWalletId(wallets[0].getId());
            transfer.setToWalletId(wallets[1].getId());
            transfer.setAmount(request.amount());
            transfer.setNote(request.note());
            transfer.setOccurredAt(request.occurredAt());
            walletTransferDao.update(transfer);
            return WalletTransferResponse.from(transfer);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletTransferService.update", e);
        }
    }

    /**
     * @param existing the transfer being edited, or null when creating — its own amount is already part of
     *                 the wallets' current balances, so it must be taken out before checking the new amount.
     */
    private Wallet[] requireValidWallets(Long familyId, CreateWalletTransferRequest request, WalletTransfer existing) {
        if (request.fromWalletId().equals(request.toWalletId())) {
            throw logged(log, new BadRequestException("Ví nguồn và ví đích phải khác nhau"));
        }
        if (request.amount().compareTo(MIN_AMOUNT) < 0) {
            throw logged(log, new BadRequestException("Số tiền chuyển phải >= 0.01"));
        }
        Wallet from = walletService.requireOwnedByFamily(request.fromWalletId(), familyId);
        Wallet to = walletService.requireOwnedByFamily(request.toWalletId(), familyId);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw logged(log, new BadRequestException("Hai ví phải cùng loại tiền tệ"));
        }
        // Balance is checked against the SOURCE wallet (you can't send more than it holds), not the
        // destination — and must include the wallet's initialBalance, not just its transaction history.
        BigDecimal fromBalance = walletService.currentBalanceOf(from);
        if (existing != null) {
            if (existing.getFromWalletId().equals(from.getId())) {
                fromBalance = fromBalance.add(existing.getAmount());
            }
            if (existing.getToWalletId().equals(from.getId())) {
                fromBalance = fromBalance.subtract(existing.getAmount());
            }
        }
        if (request.amount().compareTo(fromBalance) > 0) {
            throw logged(log, new BadRequestException("Số tiền chuyển phải <= số dư hiện tại của ví nguồn: " + fromBalance));
        }
        return new Wallet[] {from, to};
    }

    private WalletTransfer requireOwnedByFamily(Long transferId, Long familyId) {
        return walletTransferDao.selectById(transferId)
                .filter(t -> t.getFamilyId().equals(familyId))
                .orElseThrow(() -> logged(log, new NotFoundException("Giao dịch chuyển tiền không tồn tại: " + transferId)));
    }

    private void requireCreatorOrOwner(WalletTransfer transfer, Long userId, String role, String action) {
        if (!ROLE_OWNER.equals(role) && !transfer.getCreatedByUserId().equals(userId)) {
            throw logged(log, new AccessDeniedException(
                    "Chỉ người tạo hoặc chủ gia đình mới được " + action + " giao dịch chuyển tiền"));
        }
    }

    public PageResponse<WalletTransferResponse> listByFamilyPaged(Long familyId, int page, int size) {
        try {
            log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long totalElements = walletTransferDao.countByFamilyId(familyId);
            List<WalletTransferResponse> content = walletTransferDao.selectByFamilyIdPaged(familyId, size, page * size)
                    .stream()
                    .map(WalletTransferResponse::from)
                    .toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletTransferService.listByFamilyPaged", e);
        }
    }

    public void delete(Long familyId, Long userId, String role, Long transferId) {
        try {
            log.info("delete - start, familyId={}, userId={}, transferId={}", familyId, userId, transferId);
            WalletTransfer transfer = requireOwnedByFamily(transferId, familyId);
            requireCreatorOrOwner(transfer, userId, role, "xoá");
            walletTransferDao.delete(transfer);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletTransferService.delete", e);
        }
    }
}
