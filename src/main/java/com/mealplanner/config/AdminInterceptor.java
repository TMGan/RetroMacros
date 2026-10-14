package com.mealplanner.config;

import com.mealplanner.model.User;
import com.mealplanner.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Blocks every /admin/** request unless the signed-in user is an admin. */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;

    public AdminInterceptor(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (auth != null && auth.getPrincipal() instanceof OAuth2User)
                ? currentUserService.find((OAuth2User) auth.getPrincipal()) : null;
        if (user == null || !user.isAdmin()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required");
            return false;
        }
        return true;
    }
}
