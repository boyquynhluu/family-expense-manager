package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.domain.entity.WalletTransfer;
import com.family.expensemanager.expense.dto.CreateWalletTransferRequest;
import com.family.expensemanager.expense.dto.WalletTransferResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Moves money between two wallets of the same family. A transfer is neither income nor expense, so it
 * lives in its own table and only affects wallet balances (see {@link WalletService}), never the
 * transaction lists, reports or budgets.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "WalletTransferService")
public class WalletTransferService {

    private static final String ROLE_OWNER = "OWNER";
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_PAGE_SIZE = 100;

    private final WalletTransferDao walletTransferDao;
    private final WalletService walletService;

    @Transactional
    public WalletTransferResponse create(Long familyId, Long userId, CreateWalletTransferRequest request) {
        log.info("create - start, familyId={}, from={}, to={}", familyId, request.fromWalletId(), request.toWalletId());
        if (request.fromWalletId().equals(request.toWalletId())) {
            throw new BadRequestException("Ví nguồn và ví đích phải khác nhau");
        }
        if (request.amount().compareTo(MIN_AMOUNT) < 0) {
            throw new BadRequestException("Số tiền chuyển phải >= 0.01");
        }
        Wallet from = walletService.requireOwnedByFamily(request.fromWalletId(), familyId);
        Wallet to = walletService.requireOwnedByFamily(request.toWalletId(), familyId);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BadRequestException("Hai ví phải cùng loại tiền tệ");
        }

        WalletTransfer transfer = new WalletTransfer();
        transfer.setFamilyId(familyId);
        transfer.setFromWalletId(from.getId());
        transfer.setToWalletId(to.getId());
        transfer.setAmount(request.amount());
        transfer.setNote(request.note());
        transfer.setOccurredAt(request.occurredAt());
        transfer.setCreatedByUserId(userId);
        transfer.setCreatedAt(LocalDateTime.now());
        walletTransferDao.insert(transfer);
        return WalletTransferResponse.from(transfer);
    }

    public PageResponse<WalletTransferResponse> listByFamilyPaged(Long familyId, int page, int size) {
        log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
        long totalElements = walletTransferDao.countByFamilyId(familyId);
        List<WalletTransferResponse> content = walletTransferDao.selectByFamilyIdPaged(familyId, size, page * size)
                .stream()
                .map(WalletTransferResponse::from)
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    @Transactional
    public void delete(Long familyId, Long userId, String role, Long transferId) {
        log.info("delete - start, familyId={}, userId={}, transferId={}", familyId, userId, transferId);
        WalletTransfer transfer = walletTransferDao.selectById(transferId)
                .filter(t -> t.getFamilyId().equals(familyId))
                .orElseThrow(() -> new NotFoundException("Giao dịch chuyển tiền không tồn tại: " + transferId));
        boolean isOwner = ROLE_OWNER.equals(role);
        if (!isOwner && !transfer.getCreatedByUserId().equals(userId)) {
            throw new AccessDeniedException("Chỉ người tạo hoặc chủ gia đình mới được xoá giao dịch chuyển tiền");
        }
        walletTransferDao.delete(transfer);
    }
}
