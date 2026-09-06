package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateWalletRequest;
import com.family.expensemanager.expense.dto.WalletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {

    private final WalletDao walletDao;

    public WalletService(WalletDao walletDao) {
        this.walletDao = walletDao;
    }

    @Transactional
    public WalletResponse create(Long familyId, CreateWalletRequest request) {
        Wallet wallet = new Wallet();
        wallet.setFamilyId(familyId);
        wallet.setName(request.name());
        wallet.setCurrency(request.currency());
        wallet.setInitialBalance(request.initialBalance());
        walletDao.insert(wallet);
        return WalletResponse.from(wallet);
    }

    public List<WalletResponse> listByFamily(Long familyId) {
        return walletDao.selectByFamilyId(familyId).stream().map(WalletResponse::from).toList();
    }

    Wallet requireOwnedByFamily(Long walletId, Long familyId) {
        Wallet wallet = walletDao.selectById(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet không tồn tại: " + walletId));
        if (!wallet.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Wallet không tồn tại: " + walletId);
        }
        return wallet;
    }
}
