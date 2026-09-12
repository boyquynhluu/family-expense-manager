package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateWalletRequest;
import com.family.expensemanager.expense.dto.WalletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "WalletService")
public class WalletService {

    private static final String TYPE_INCOME = "INCOME";
    private static final String TYPE_EXPENSE = "EXPENSE";

    private final WalletDao walletDao;
    private final TransactionDao transactionDao;

    @Transactional
    public WalletResponse create(Long familyId, CreateWalletRequest request) {
        log.info("create - start, familyId={}, name={}", familyId, request.name());
        Wallet wallet = new Wallet();
        wallet.setFamilyId(familyId);
        wallet.setName(request.name());
        wallet.setCurrency(request.currency());
        wallet.setInitialBalance(request.initialBalance());
        walletDao.insert(wallet);
        return WalletResponse.from(wallet);
    }

    public List<WalletResponse> listByFamily(Long familyId) {
        log.info("listByFamily - start, familyId={}", familyId);
        return walletDao.selectByFamilyId(familyId).stream()
                .map(wallet -> WalletResponse.from(wallet, currentBalanceOf(wallet)))
                .toList();
    }

    @Transactional
    public WalletResponse update(Long walletId, Long familyId, CreateWalletRequest request) {
        log.info("update - start, walletId={}, familyId={}", walletId, familyId);
        Wallet wallet = requireOwnedByFamily(walletId, familyId);
        wallet.setName(request.name());
        wallet.setCurrency(request.currency());
        wallet.setInitialBalance(request.initialBalance());
        walletDao.update(wallet);
        return WalletResponse.from(wallet, currentBalanceOf(wallet));
    }

    private BigDecimal currentBalanceOf(Wallet wallet) {
        BigDecimal income = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_INCOME);
        BigDecimal expense = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_EXPENSE);
        return wallet.getInitialBalance().add(income).subtract(expense);
    }

    Wallet requireOwnedByFamily(Long walletId, Long familyId) {
        log.info("requireOwnedByFamily - start, walletId={}, familyId={}", walletId, familyId);
        Wallet wallet = walletDao.selectById(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet không tồn tại: " + walletId));
        if (!wallet.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Wallet không tồn tại: " + walletId);
        }
        return wallet;
    }
}
