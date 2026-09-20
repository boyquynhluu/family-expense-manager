package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.CategoryResponse;
import com.family.expensemanager.expense.dto.CreateCategoryRequest;
import com.family.expensemanager.expense.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses/categories")
@RequiredArgsConstructor
@Slf4j(topic = "CategoryController")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        log.info("create - start");
        return ApiResponse.ok(categoryService.create(CurrentUser.familyId(), request));
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> list() {
        log.info("list - start");
        return ApiResponse.ok(categoryService.listByFamily(CurrentUser.familyId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CreateCategoryRequest request) {
        log.info("update - start, id={}", id);
        return ApiResponse.ok(categoryService.update(id, CurrentUser.familyId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("delete - start, id={}", id);
        categoryService.delete(id, CurrentUser.familyId());
        return ApiResponse.ok();
    }

    @GetMapping("/trash")
    public ApiResponse<PageResponse<CategoryResponse>> trash(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        log.info("trash - start, page={}, size={}", page, size);
        return ApiResponse.ok(categoryService.listDeletedPaged(CurrentUser.familyId(), page, size));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        log.info("restore - start, id={}", id);
        categoryService.restore(id, CurrentUser.familyId());
        return ApiResponse.ok();
    }
}
