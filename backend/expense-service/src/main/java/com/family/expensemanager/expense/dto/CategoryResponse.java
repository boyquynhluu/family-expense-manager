package com.family.expensemanager.expense.dto;

import com.family.expensemanager.expense.domain.entity.Category;

import java.time.LocalDateTime;

/**
 * @author boyquynhluu
 */
public record CategoryResponse(
        Long id, Long familyId, String name, String type, String icon, String color, LocalDateTime deletedAt,
        Long parentId) {

    /** A top-level category (before README C6). */
    public CategoryResponse(Long id, Long familyId, String name, String type, String icon, String color,
                            LocalDateTime deletedAt) {
        this(id, familyId, name, type, icon, color, deletedAt, null);
    }

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getFamilyId(), category.getName(),
                category.getType(), category.getIcon(), category.getColor(), category.getDeletedAt(),
                category.getParentId());
    }
}
