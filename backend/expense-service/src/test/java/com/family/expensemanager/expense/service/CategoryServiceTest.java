package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.dto.CreateCategoryRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryDao categoryDao;
    @Mock
    private TransactionDao transactionDao;
    @Mock
    private BudgetDao budgetDao;
    @Mock
    private RecurringTransactionDao recurringTransactionDao;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryDao, transactionDao, budgetDao, recurringTransactionDao);
    }

    @Test
    void create_savesCategory_withFamilyId() {
        var response = categoryService.create(1L, new CreateCategoryRequest("Ăn uống", "EXPENSE", "food", "#ff0000"));

        assertThat(response.name()).isEqualTo("Ăn uống");
        assertThat(response.familyId()).isEqualTo(1L);
    }

    @Test
    void update_throwsNotFound_whenCategoryBelongsToAnotherFamily() {
        Category category = category(1L, 2L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(1L, 1L, new CreateCategoryRequest("X", "EXPENSE", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_throwsNotFound_whenCategoryMissing() {
        when(categoryDao.selectById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(1L, 1L, new CreateCategoryRequest("X", "EXPENSE", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_throwsConflict_whenCategoryHasTransactions() {
        Category category = category(1L, 1L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));
        when(transactionDao.countByCategoryId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(categoryDao, never()).delete(any());
    }

    @Test
    void delete_throwsConflict_whenCategoryHasBudgets() {
        Category category = category(1L, 1L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));
        when(transactionDao.countByCategoryId(1L)).thenReturn(0L);
        when(budgetDao.countByCategoryId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(categoryDao, never()).delete(any());
    }

    @Test
    void delete_throwsConflict_whenCategoryHasRecurringTransactions() {
        Category category = category(1L, 1L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));
        when(transactionDao.countByCategoryId(1L)).thenReturn(0L);
        when(budgetDao.countByCategoryId(1L)).thenReturn(0L);
        when(recurringTransactionDao.countByCategoryId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(categoryDao, never()).delete(any());
    }

    @Test
    void delete_succeeds_whenCategoryUnused() {
        Category category = category(1L, 1L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));
        when(transactionDao.countByCategoryId(1L)).thenReturn(0L);
        when(budgetDao.countByCategoryId(1L)).thenReturn(0L);
        when(recurringTransactionDao.countByCategoryId(1L)).thenReturn(0L);

        categoryService.delete(1L, 1L);

        verify(categoryDao).delete(category);
    }

    private static Category category(Long id, Long familyId) {
        Category category = new Category();
        category.setId(id);
        category.setFamilyId(familyId);
        category.setName("Category " + id);
        category.setType("EXPENSE");
        return category;
    }
}
