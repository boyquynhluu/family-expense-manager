package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.service.MonthlySummaryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * README C7: the OWNER sends a past month's summary now instead of waiting for the 1st.
 *
 * @author boyquynhluu
 */
@RestController
@RequestMapping("/api/expenses/monthly-summary")
@RequiredArgsConstructor
@Slf4j(topic = "MonthlySummaryController")
public class MonthlySummaryController {

    private final MonthlySummaryService monthlySummaryService;

    @PostMapping("/send")
    public ApiResponse<Void> send(@RequestParam String yearMonth) {
        log.info("send - start, yearMonth={}", yearMonth);
        monthlySummaryService.sendNow(CurrentUser.familyId(), yearMonth);
        return ApiResponse.ok();
    }
}
