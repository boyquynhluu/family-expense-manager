package com.family.expensemanager.expense.service;

import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.SeedDefaultsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Gives a brand-new family the minimum data (a wallet + categories) it needs to record its first transaction. */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "OnboardingService")
public class OnboardingService {

    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final String TYPE_INCOME = "INCOME";
    private static final String DEFAULT_WALLET_NAME = "Tiền mặt";
    private static final String DEFAULT_CURRENCY = "VND";

    private record DefaultCategory(String name, String type, String icon, String color) {
    }

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("Ăn uống", TYPE_EXPENSE, "🍜", "#f97316"),
            new DefaultCategory("Đi lại", TYPE_EXPENSE, "🚌", "#0ea5e9"),
            new DefaultCategory("Nhà cửa & hoá đơn", TYPE_EXPENSE, "🏠", "#8b5cf6"),
            new DefaultCategory("Mua sắm", TYPE_EXPENSE, "🛍️", "#ec4899"),
            new DefaultCategory("Sức khoẻ", TYPE_EXPENSE, "💊", "#ef4444"),
            new DefaultCategory("Giáo dục", TYPE_EXPENSE, "📚", "#14b8a6"),
            new DefaultCategory("Giải trí", TYPE_EXPENSE, "🎬", "#eab308"),
            new DefaultCategory("Khác", TYPE_EXPENSE, "📦", "#6b7280"),
            new DefaultCategory("Lương", TYPE_INCOME, "💰", "#22c55e"),
            new DefaultCategory("Thưởng", TYPE_INCOME, "🎁", "#10b981"),
            new DefaultCategory("Thu nhập khác", TYPE_INCOME, "➕", "#84cc16"));

    private final CategoryDao categoryDao;
    private final WalletDao walletDao;

    /** Each half is independent and a no-op once the family already has that kind of data, so re-calling is safe. */
    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public SeedDefaultsResponse seedDefaults(Long familyId) {
        log.info("seedDefaults - start, familyId={}", familyId);
        int categoriesCreated = 0;
        if (categoryDao.selectByFamilyId(familyId).isEmpty()) {
            for (DefaultCategory defaults : DEFAULT_CATEGORIES) {
                Category category = new Category();
                category.setFamilyId(familyId);
                category.setName(defaults.name());
                category.setType(defaults.type());
                category.setIcon(defaults.icon());
                category.setColor(defaults.color());
                categoryDao.insert(category);
                categoriesCreated++;
            }
        }

        int walletsCreated = 0;
        if (walletDao.selectByFamilyId(familyId).isEmpty()) {
            Wallet wallet = new Wallet();
            wallet.setFamilyId(familyId);
            wallet.setName(DEFAULT_WALLET_NAME);
            wallet.setCurrency(DEFAULT_CURRENCY);
            wallet.setInitialBalance(BigDecimal.ZERO);
            walletDao.insert(wallet);
            walletsCreated++;
        }
        log.info("seedDefaults - done, familyId={}, categoriesCreated={}, walletsCreated={}",
                familyId, categoriesCreated, walletsCreated);
        return new SeedDefaultsResponse(categoriesCreated, walletsCreated);
    }
}
