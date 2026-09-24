package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
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
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.List;
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
    void requireOwnedByFamily_withType_returnsCategory_whenTypeMatches() {
        Category category = category(1L, 1L);
        category.setType("EXPENSE");
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));

        assertThat(categoryService.requireOwnedByFamily(1L, 1L, "EXPENSE")).isSameAs(category);
    }

    @Test
    void requireOwnedByFamily_withType_throwsBadRequest_whenTypeDiffers() {
        Category category = category(1L, 1L);
        category.setType("INCOME");
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.requireOwnedByFamily(1L, 1L, "EXPENSE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("INCOME")
                .hasMessageContaining("EXPENSE");
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
        verify(categoryDao, never()).update(any());
    }

    @Test
    void delete_throwsConflict_whenCategoryHasBudgets() {
        Category category = category(1L, 1L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));
        when(transactionDao.countByCategoryId(1L)).thenReturn(0L);
        when(budgetDao.countByCategoryId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(categoryDao, never()).update(any());
    }

    @Test
    void delete_throwsConflict_whenCategoryHasRecurringTransactions() {
        Category category = category(1L, 1L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));
        when(transactionDao.countByCategoryId(1L)).thenReturn(0L);
        when(budgetDao.countByCategoryId(1L)).thenReturn(0L);
        when(recurringTransactionDao.countByCategoryId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(categoryDao, never()).update(any());
    }

    @Test
    void delete_succeeds_whenCategoryUnused() {
        Category category = category(1L, 1L);
        when(categoryDao.selectById(1L)).thenReturn(Optional.of(category));
        when(transactionDao.countByCategoryId(1L)).thenReturn(0L);
        when(budgetDao.countByCategoryId(1L)).thenReturn(0L);
        when(recurringTransactionDao.countByCategoryId(1L)).thenReturn(0L);

        categoryService.delete(1L, 1L);

        assertThat(category.getDeletedAt()).isNotNull();
        verify(categoryDao).update(category);
    }

    @Test
    void restore_clearsDeletedAt_whenRowExists() {
        when(categoryDao.restore(1L, 1L)).thenReturn(1);

        categoryService.restore(1L, 1L);

        verify(categoryDao).restore(1L, 1L);
    }

    @Test
    void restore_throwsNotFound_whenRowMissingOrNotDeleted() {
        when(categoryDao.restore(1L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> categoryService.restore(1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listDeletedPaged_returnsPageWithOffset() {
        when(categoryDao.countDeletedByFamilyId(1L)).thenReturn(12L);
        when(categoryDao.selectDeletedByFamilyIdPaged(1L, 5, 10))
                .thenReturn(List.of(category(11L, 1L), category(12L, 1L)));

        var result = categoryService.listDeletedPaged(1L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).id()).isEqualTo(11L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listDeletedPaged_rejectsNegativePage() {
        assertThatThrownBy(() -> categoryService.listDeletedPaged(1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listDeletedPaged_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> categoryService.listDeletedPaged(1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> categoryService.listDeletedPaged(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    private static Category category(Long id, Long familyId) {
        Category category = new Category();
        category.setId(id);
        category.setFamilyId(familyId);
        category.setName("Category " + id);
        category.setType("EXPENSE");
        return category;
    }

    @Test
    void mutatingMethods_requireOwnerRole() {
        for (String name : List.of("create", "update", "delete", "restore")) {
            var methods = Arrays.stream(CategoryService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).toList();
            assertThat(methods).as(name).isNotEmpty();
            assertThat(methods).as(name).allSatisfy(m -> {
                PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasRole('OWNER')");
            });
        }
    }
}
