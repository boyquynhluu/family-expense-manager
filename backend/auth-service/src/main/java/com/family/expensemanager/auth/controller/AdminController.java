package com.family.expensemanager.auth.controller;

import com.family.expensemanager.auth.dto.FamilyAdminResponse;
import com.family.expensemanager.auth.dto.MessageResponse;
import com.family.expensemanager.auth.dto.SetSystemAdminRequest;
import com.family.expensemanager.auth.dto.SetUserLockedRequest;
import com.family.expensemanager.auth.dto.UserAdminResponse;
import com.family.expensemanager.auth.service.AdminService;
import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<PageResponse<FamilyAdminResponse>> families(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "5") int size,
                                                                   @RequestParam(required = false) String q) {
        log.info("families - start, page={}, size={}", page, size);
        return ApiResponse.ok(adminService.listFamiliesPaged(page, size, q));
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserAdminResponse>> users(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "5") int size,
                                                              @RequestParam(required = false) String q) {
        log.info("users - start, page={}, size={}", page, size);
        return ApiResponse.ok(adminService.listUsersPaged(page, size, q));
    }

    @PutMapping("/users/{id}/system-admin")
    public ApiResponse<MessageResponse> setSystemAdmin(@PathVariable Long id,
                                                         @Valid @RequestBody SetSystemAdminRequest request) {
        log.info("setSystemAdmin - start, id={}", id);
        adminService.setSystemAdmin(id, request.isSystemAdmin());
        return ApiResponse.ok(new MessageResponse("Đã cập nhật quyền admin"));
    }

    @PutMapping("/users/{id}/locked")
    public ApiResponse<MessageResponse> setLocked(@PathVariable Long id,
                                                   @Valid @RequestBody SetUserLockedRequest request) {
        log.info("setLocked - start, id={}", id);
        adminService.setLocked(id, request.locked());
        return ApiResponse.ok(new MessageResponse(request.locked() ? "Đã khoá tài khoản" : "Đã mở khoá tài khoản"));
    }
}
