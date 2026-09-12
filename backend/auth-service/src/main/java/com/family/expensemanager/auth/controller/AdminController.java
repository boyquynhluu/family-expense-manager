package com.family.expensemanager.auth.controller;

import com.family.expensemanager.auth.dto.FamilyAdminResponse;
import com.family.expensemanager.auth.dto.MessageResponse;
import com.family.expensemanager.auth.dto.SetSystemAdminRequest;
import com.family.expensemanager.auth.dto.UserAdminResponse;
import com.family.expensemanager.auth.service.AdminService;
import com.family.expensemanager.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * System-admin-only endpoints (see {@link AdminService} for the authorization check).
 * A regular authenticated user hitting these gets a 403 from the {@code @PreAuthorize}
 * on the service methods, same pattern as the OWNER-only wallet/category/budget deletes.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j(topic = "AdminController")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/families")
    public ApiResponse<List<FamilyAdminResponse>> families() {
        log.info("families - start");
        return ApiResponse.ok(adminService.listFamilies());
    }

    @GetMapping("/users")
    public ApiResponse<List<UserAdminResponse>> users() {
        log.info("users - start");
        return ApiResponse.ok(adminService.listUsers());
    }

    @PutMapping("/users/{id}/system-admin")
    public ApiResponse<MessageResponse> setSystemAdmin(@PathVariable Long id,
                                                         @Valid @RequestBody SetSystemAdminRequest request) {
        log.info("setSystemAdmin - start, id={}", id);
        adminService.setSystemAdmin(id, request.isSystemAdmin());
        return ApiResponse.ok(new MessageResponse("Đã cập nhật quyền admin"));
    }
}
