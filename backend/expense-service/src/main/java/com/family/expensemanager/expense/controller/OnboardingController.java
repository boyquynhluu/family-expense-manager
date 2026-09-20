package com.family.expensemanager.expense.controller;

import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dto.SeedDefaultsResponse;
import com.family.expensemanager.expense.service.OnboardingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/expenses/onboarding")
@RequiredArgsConstructor
@Slf4j(topic = "OnboardingController")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/seed-defaults")
    public ApiResponse<SeedDefaultsResponse> seedDefaults() {
        log.info("seedDefaults - start");
        return ApiResponse.ok(onboardingService.seedDefaults(CurrentUser.familyId()));
    }
}
