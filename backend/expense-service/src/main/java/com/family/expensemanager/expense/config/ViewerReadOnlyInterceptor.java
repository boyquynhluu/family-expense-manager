package com.family.expensemanager.expense.config;

import com.family.expensemanager.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * README A5: a VIEWER ("chỉ xem") may read everything of the family but change nothing — every non-read request
 * to expense-service is refused here, before any controller, instead of a check in each service method. The
 * exception goes through the normal exception handlers (403 with the usual {"message": ...} body).
 *
 * @author boyquynhluu
 */
@Component
public class ViewerReadOnlyInterceptor implements HandlerInterceptor {

    static final String ROLE_VIEWER = "ROLE_VIEWER";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (READ_METHODS.contains(request.getMethod())) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean viewer = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_VIEWER.equals(a.getAuthority()));
        if (viewer) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn đang ở vai trò chỉ xem — không thể thêm, sửa hay xoá dữ liệu");
        }
        return true;
    }
}
