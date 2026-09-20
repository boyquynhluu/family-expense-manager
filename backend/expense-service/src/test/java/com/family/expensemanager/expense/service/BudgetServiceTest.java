package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetDao budgetDao;
    @Mock
    private CategoryService categoryService;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetDao, categoryService);
    }

    @Test
    void create_savesBudget_whenCategoryOwnedByFamily() {
        var response = budgetService.create(1L, new CreateBudgetRequest(5L, "2026-01", BigDecimal.valueOf(1000)));

        verify(categoryService).requireOwnedByFamily(5L, 1L);
        assertThat(response.familyId()).isEqualTo(1L);
        assertThat(response.categoryId()).isEqualTo(5L);
    }

    @Test
    void create_propagatesNotFound_whenCategoryNotOwnedByFamily() {
        doThrow(new NotFoundException("Category không tồn tại: 5"))
                .when(categoryService).requireOwnedByFamily(5L, 1L);

        assertThatThrownBy(() -> budgetService.create(1L, new CreateBudgetRequest(5L, "2026-01", BigDecimal.TEN)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_throwsNotFound_whenBudgetBelongsToAnotherFamily() {
        Budget budget = budget(1L, 2L);
        when(budgetDao.selectById(1L)).thenReturn(Optional.of(budget));

        assertThatThrownBy(() -> budgetService.update(1L, 1L, new CreateBudgetRequest(5L, "2026-01", BigDecimal.TEN)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_throwsNotFound_whenBudgetMissing() {
        when(budgetDao.selectById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.update(1L, 1L, new CreateBudgetRequest(5L, "2026-01", BigDecimal.TEN)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_throwsNotFound_whenBudgetBelongsToAnotherFamily() {
        Budget budget = budget(1L, 2L);
        when(budgetDao.selectById(1L)).thenReturn(Optional.of(budget));

        assertThatThrownBy(() -> budgetService.delete(1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_succeeds_whenBudgetOwnedByFamily() {
        Budget budget = budget(1L, 1L);
        when(budgetDao.selectById(1L)).thenReturn(Optional.of(budget));

        budgetService.delete(1L, 1L);

        verify(budgetDao).delete(budget);
    }

    @Test
    void listByFamilyPaged_returnsPageWithOffset() {
        when(budgetDao.countByFamilyId(1L)).thenReturn(12L);
        when(budgetDao.selectByFamilyIdPaged(1L, 5, 10)).thenReturn(List.of(budget(11L, 1L), budget(12L, 1L)));

        var result = budgetService.listByFamilyPaged(1L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listByFamilyPaged_rejectsNegativePage() {
        assertThatThrownBy(() -> budgetService.listByFamilyPaged(1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamilyPaged_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> budgetService.listByFamilyPaged(1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> budgetService.listByFamilyPaged(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    private static Budget budget(Long id, Long familyId) {
        Budget budget = new Budget();
        budget.setId(id);
        budget.setFamilyId(familyId);
        budget.setCategoryId(5L);
        budget.setPeriodMonth("2026-01");
        budget.setLimitAmount(BigDecimal.TEN);
        return budget;
    }
}
