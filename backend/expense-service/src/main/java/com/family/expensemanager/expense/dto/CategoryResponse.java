package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Category;

public record CategoryResponse(Long id, Long familyId, String name, String type, String icon, String color) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getFamilyId(), category.getName(),
                category.getType(), category.getIcon(), category.getColor());
    }
}
