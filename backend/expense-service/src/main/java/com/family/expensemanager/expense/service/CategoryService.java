package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.dto.CategoryResponse;
import com.family.expensemanager.expense.dto.CreateCategoryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryDao categoryDao;

    public CategoryService(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    @Transactional
    public CategoryResponse create(Long familyId, CreateCategoryRequest request) {
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
        return categoryDao.selectByFamilyId(familyId).stream().map(CategoryResponse::from).toList();
    }

    Category requireOwnedByFamily(Long categoryId, Long familyId) {
        Category category = categoryDao.selectById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category không tồn tại: " + categoryId));
        if (!category.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Category không tồn tại: " + categoryId);
        }
        return category;
    }
}
