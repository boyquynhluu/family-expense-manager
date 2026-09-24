package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.dto.CategoryResponse;
import com.family.expensemanager.expense.dto.CreateCategoryRequest;
import java.io.UncheckedIOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "CategoryService")
public class CategoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryDao categoryDao;
    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final RecurringTransactionDao recurringTransactionDao;

    @PreAuthorize("hasRole('OWNER')")
    public CategoryResponse create(Long familyId, CreateCategoryRequest request) {
        try {
            log.info("create - start, familyId={}, name={}", familyId, request.name());
            Category category = new Category();
            category.setFamilyId(familyId);
            category.setName(request.name());
            category.setType(request.type());
            category.setIcon(request.icon());
            category.setColor(request.color());
            categoryDao.insert(category);
            return CategoryResponse.from(category);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("CategoryService.create", e);
        }
    }

    public List<CategoryResponse> listByFamily(Long familyId) {
        try {
            log.info("listByFamily - start, familyId={}", familyId);
            return categoryDao.selectByFamilyId(familyId).stream().map(CategoryResponse::from).toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("CategoryService.listByFamily", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public CategoryResponse update(Long categoryId, Long familyId, CreateCategoryRequest request) {
        try {
            log.info("update - start, categoryId={}, familyId={}", categoryId, familyId);
            Category category = requireOwnedByFamily(categoryId, familyId);
            category.setName(request.name());
            category.setType(request.type());
            category.setIcon(request.icon());
            category.setColor(request.color());
            categoryDao.update(category);
            return CategoryResponse.from(category);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("CategoryService.update", e);
        }
    }

    /** Soft-delete (README "10. Xoá là mất vĩnh viễn") — the row stays, just hidden, so {@link #restore} can undo it. */
    @PreAuthorize("hasRole('OWNER')")
    public void delete(Long categoryId, Long familyId) {
        try {
            log.info("delete - start, categoryId={}, familyId={}", categoryId, familyId);
            Category category = requireOwnedByFamily(categoryId, familyId);
            if (transactionDao.countByCategoryId(categoryId) > 0 || budgetDao.countByCategoryId(categoryId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá danh mục đang có giao dịch hoặc ngân sách"));
            }
            if (recurringTransactionDao.countByCategoryId(categoryId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá danh mục đang có giao dịch định kỳ"));
            }
            category.setDeletedAt(LocalDateTime.now());
            categoryDao.update(category);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("CategoryService.delete", e);
        }
    }

    public PageResponse<CategoryResponse> listDeletedPaged(Long familyId, int page, int size) {
        try {
            log.info("listDeletedPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long totalElements = categoryDao.countDeletedByFamilyId(familyId);
            List<CategoryResponse> content = categoryDao.selectDeletedByFamilyIdPaged(familyId, size, page * size).stream()
                    .map(CategoryResponse::from)
                    .toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("CategoryService.listDeletedPaged", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public void restore(Long categoryId, Long familyId) {
        try {
            log.info("restore - start, categoryId={}, familyId={}", categoryId, familyId);
            if (categoryDao.restore(categoryId, familyId) == 0) {
                throw logged(log, new NotFoundException("Danh mục đã xoá không tồn tại: " + categoryId));
            }
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("CategoryService.restore", e);
        }
    }

    Category requireOwnedByFamily(Long categoryId, Long familyId) {
        log.info("requireOwnedByFamily - start, categoryId={}, familyId={}", categoryId, familyId);
        Category category = categoryDao.selectById(categoryId)
                .orElseThrow(() -> logged(log, new NotFoundException("Category không tồn tại: " + categoryId)));
        if (!category.getFamilyId().equals(familyId)) {
            throw logged(log, new NotFoundException("Category không tồn tại: " + categoryId));
        }
        return category;
    }

    /** Same as above, and additionally requires the category to be of {@code expectedType} (INCOME/EXPENSE). */
    Category requireOwnedByFamily(Long categoryId, Long familyId, String expectedType) {
        Category category = requireOwnedByFamily(categoryId, familyId);
        if (!category.getType().equals(expectedType)) {
            throw logged(log, new BadRequestException("Danh mục \"" + category.getName() + "\" thuộc loại "
                    + category.getType() + ", không khớp loại " + expectedType));
        }
        return category;
    }
}
