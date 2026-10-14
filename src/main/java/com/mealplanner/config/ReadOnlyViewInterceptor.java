package com.mealplanner.config;

import com.mealplanner.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * While an admin is viewing a client account, only reads are allowed outside
 * the admin area, so nothing can be changed on the client's behalf.
 */
@Component
public class ReadOnlyViewInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final CurrentUserService currentUserService;

    public ReadOnlyViewInterceptor(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (READ_METHODS.contains(request.getMethod())) return true;
        if (!currentUserService.isViewingAs(request.getSession(false))) return true;
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Read-only while viewing a client. Exit the client view first.");
        return false;
    }
}
