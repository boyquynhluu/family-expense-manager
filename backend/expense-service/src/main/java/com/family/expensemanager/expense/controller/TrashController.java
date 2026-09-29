package com.family.expensemanager.expense.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.TrashCountResponse;
import com.family.expensemanager.expense.service.TrashService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses/trash")
@RequiredArgsConstructor
@Slf4j(topic = "TrashController")
public class TrashController {

    private final TrashService trashService;

    @GetMapping("/count")
    public ApiResponse<TrashCountResponse> getTrash() { // { wallets, categories, transactions, total }
        return ApiResponse.ok(trashService.getTotalTrash(CurrentUser.familyId()));
    }
}
