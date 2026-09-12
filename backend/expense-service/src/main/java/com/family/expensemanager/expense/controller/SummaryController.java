package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.CategoryReportItem;
import com.family.expensemanager.expense.dto.SummaryResponse;
import com.family.expensemanager.expense.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j(topic = "SummaryController")
public class SummaryController {

    private final SummaryService summaryService;

    @GetMapping("/summary")
    public ApiResponse<SummaryResponse> summary(@RequestParam String yearMonth) {
        log.info("summary - start, yearMonth={}", yearMonth);
        return ApiResponse.ok(summaryService.summary(CurrentUser.familyId(), yearMonth));
    }

    @GetMapping("/reports/category")
    public ApiResponse<List<CategoryReportItem>> reportByCategory(@RequestParam String yearMonth) {
        log.info("reportByCategory - start, yearMonth={}", yearMonth);
        return ApiResponse.ok(summaryService.reportByCategory(CurrentUser.familyId(), yearMonth));
    }
}
