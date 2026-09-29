package com.family.expensemanager.expense.service;

import java.io.UncheckedIOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dto.TrashCountResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j(topic = "TrashService")
public class TrashService {

    private final WalletDao walletDao;
    private final CategoryDao categoryDao;
    private final TransactionDao transactionDao;

    public TrashCountResponse getTotalTrash(Long familyId) {
        Long total;
        try {
            total = walletDao.countDeletedByFamilyId(familyId)
                + categoryDao.countDeletedByFamilyId(familyId)
                + transactionDao.countDeletedByFamilyId(familyId);

        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TrashService.getTotalTrash", e);
        }
        return new TrashCountResponse(total);
    }
}
