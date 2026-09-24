package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.dto.CopyBudgetsRequest;
import com.family.expensemanager.expense.dto.CreateBudgetRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

        verify(categoryService).requireOwnedByFamily(5L, 1L, "EXPENSE");
        assertThat(response.familyId()).isEqualTo(1L);
        assertThat(response.categoryId()).isEqualTo(5L);
    }

    @Test
    void create_propagatesNotFound_whenCategoryNotOwnedByFamily() {
        doThrow(new NotFoundException("Category không tồn tại: 5"))
                .when(categoryService).requireOwnedByFamily(5L, 1L, "EXPENSE");

        assertThatThrownBy(() -> budgetService.create(1L, new CreateBudgetRequest(5L, "2026-01", BigDecimal.TEN)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_savesOverallBudget_whenCategoryIdNull() {
        var response = budgetService.create(1L, new CreateBudgetRequest(null, "2026-01", BigDecimal.valueOf(1000)));

        verify(categoryService, never()).requireOwnedByFamily(any(), any(), any());
        assertThat(response.categoryId()).isNull();
        assertThat(response.familyId()).isEqualTo(1L);
    }

    @Test
    void create_rejectsSecondOverallBudget_forSameMonth() {
        when(budgetDao.selectOverallByPeriod(1L, "2026-01")).thenReturn(Optional.of(overallBudget(3L)));

        assertThatThrownBy(() -> budgetService.create(1L, new CreateBudgetRequest(null, "2026-01", BigDecimal.TEN)))
                .isInstanceOf(BadRequestException.class);
        verify(budgetDao, never()).insert(any());
    }

    @Test
    void create_rejectsDuplicateCategoryBudget_forSameMonth() {
        when(budgetDao.selectByCategoryAndPeriod(5L, "2026-01")).thenReturn(Optional.of(budget(3L, 1L)));

        assertThatThrownBy(() -> budgetService.create(1L, new CreateBudgetRequest(5L, "2026-01", BigDecimal.TEN)))
                .isInstanceOf(BadRequestException.class);
        verify(budgetDao, never()).insert(any());
    }

    @Test
    void update_allowsSavingOverallBudget_thatIsItself() {
        Budget existing = overallBudget(3L);
        when(budgetDao.selectById(3L)).thenReturn(Optional.of(existing));
        when(budgetDao.selectOverallByPeriod(1L, "2026-01")).thenReturn(Optional.of(existing));

        var response = budgetService.update(3L, 1L, new CreateBudgetRequest(null, "2026-01", BigDecimal.valueOf(99)));

        assertThat(response.limitAmount()).isEqualByComparingTo("99");
        verify(budgetDao).update(existing);
    }

    @Test
    void update_rejectsOverallBudget_whenAnotherOneExistsInMonth() {
        when(budgetDao.selectById(4L)).thenReturn(Optional.of(budget(4L, 1L)));
        when(budgetDao.selectOverallByPeriod(1L, "2026-01")).thenReturn(Optional.of(overallBudget(3L)));

        assertThatThrownBy(() -> budgetService.update(4L, 1L, new CreateBudgetRequest(null, "2026-01", BigDecimal.TEN)))
                .isInstanceOf(BadRequestException.class);
        verify(budgetDao, never()).update(any());
    }

    @Test
    void copy_copiesMissingBudgets_andSkipsExistingOnes() {
        Budget food = budget(1L, 1L);
        Budget overall = overallBudget(2L);
        Budget transport = budget(3L, 1L);
        transport.setCategoryId(6L);
        Budget existingFoodInTarget = budget(9L, 1L);
        existingFoodInTarget.setPeriodMonth("2026-02");
        when(budgetDao.selectByFamilyAndPeriod(1L, "2026-01")).thenReturn(List.of(food, overall, transport));
        when(budgetDao.selectByFamilyAndPeriod(1L, "2026-02")).thenReturn(List.of(existingFoodInTarget));

        var result = budgetService.copy(1L, new CopyBudgetsRequest("2026-01", "2026-02"));

        assertThat(result.copied()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetDao, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Budget::getCategoryId).containsExactly(null, 6L);
        assertThat(captor.getAllValues()).allSatisfy(b -> {
            assertThat(b.getFamilyId()).isEqualTo(1L);
            assertThat(b.getPeriodMonth()).isEqualTo("2026-02");
        });
    }

    @Test
    void copy_skipsExistingOverallBudget() {
        when(budgetDao.selectByFamilyAndPeriod(1L, "2026-01")).thenReturn(List.of(overallBudget(2L)));
        when(budgetDao.selectByFamilyAndPeriod(1L, "2026-02")).thenReturn(List.of(overallBudget(8L)));

        var result = budgetService.copy(1L, new CopyBudgetsRequest("2026-01", "2026-02"));

        assertThat(result.copied()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(budgetDao, never()).insert(any());
    }

    @Test
    void copy_rejectsSameMonth() {
        assertThatThrownBy(() -> budgetService.copy(1L, new CopyBudgetsRequest("2026-01", "2026-01")))
                .isInstanceOf(BadRequestException.class);
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

    private static Budget overallBudget(Long id) {
        Budget budget = budget(id, 1L);
        budget.setCategoryId(null);
        return budget;
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

    @Test
    void mutatingMethods_requireOwnerRole() {
        for (String name : List.of("create", "update", "copy", "delete")) {
            var methods = Arrays.stream(BudgetService.class.getDeclaredMethods())
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
