package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.dto.CategoryResponse;
import com.family.expensemanager.expense.dto.CreateCategoryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CategoryService")
public class CategoryService {

    private final CategoryDao categoryDao;

    @Transactional
    public CategoryResponse create(Long familyId, CreateCategoryRequest request) {
        log.info("create - start, familyId={}, name={}", familyId, request.name());
        Category category = new Category();
        category.setFamilyId(familyId);
        category.setName(request.name());
        category.setType(request.type());
        category.setIcon(request.icon());
        category.setColor(request.color());
        categoryDao.insert(category);
        return CategoryResponse.from(category);
    }

    public List<CategoryResponse> listByFamily(Long familyId) {
        log.info("listByFamily - start, familyId={}", familyId);
        return categoryDao.selectByFamilyId(familyId).stream().map(CategoryResponse::from).toList();
    }

    Category requireOwnedByFamily(Long categoryId, Long familyId) {
        log.info("requireOwnedByFamily - start, categoryId={}, familyId={}", categoryId, familyId);
        Category category = categoryDao.selectById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category không tồn tại: " + categoryId));
        if (!category.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Category không tồn tại: " + categoryId);
        }
        return category;
    }
}
