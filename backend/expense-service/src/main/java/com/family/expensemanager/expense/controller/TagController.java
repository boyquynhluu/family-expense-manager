package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dao.TagDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * README C6: the family's tags (for the filter and the autocomplete). Tags are created by tagging a transaction.
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/tags")
@RequiredArgsConstructor
public class TagController {

    public record TagResponse(Long id, String name) {
    }

    private final TagDao tagDao;

    @GetMapping
    public ApiResponse<List<TagResponse>> list() {
        return ApiResponse.ok(tagDao.selectByFamilyId(CurrentUser.familyId()).stream()
                .map(t -> new TagResponse(t.getId(), t.getName()))
                .toList());
    }
}
