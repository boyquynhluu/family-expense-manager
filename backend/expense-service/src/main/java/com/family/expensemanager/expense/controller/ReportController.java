package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.CompareReportResponse;
import com.family.expensemanager.expense.dto.MemberReportItem;
import com.family.expensemanager.expense.dto.RangeReportResponse;
import com.family.expensemanager.expense.dto.YearReportResponse;
import com.family.expensemanager.expense.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses/reports")
@RequiredArgsConstructor
@Slf4j(topic = "ReportController")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/range")
    public ApiResponse<RangeReportResponse> range(@RequestParam String from, @RequestParam String to) {
        log.info("range - start, from={}, to={}", from, to);
        return ApiResponse.ok(reportService.range(CurrentUser.familyId(), from, to));
    }

    @GetMapping("/year")
    public ApiResponse<YearReportResponse> year(@RequestParam String year) {
        log.info("year - start, year={}", year);
        return ApiResponse.ok(reportService.year(CurrentUser.familyId(), year));
    }

    @GetMapping("/by-member")
    public ApiResponse<List<MemberReportItem>> byMember(@RequestParam String from, @RequestParam String to) {
        log.info("byMember - start, from={}, to={}", from, to);
        return ApiResponse.ok(reportService.byMember(CurrentUser.familyId(), from, to));
    }

    @GetMapping("/compare")
    public ApiResponse<CompareReportResponse> compare(@RequestParam String month, @RequestParam String withMonth) {
        log.info("compare - start, month={}, withMonth={}", month, withMonth);
        return ApiResponse.ok(reportService.compare(CurrentUser.familyId(), month, withMonth));
    }
}
