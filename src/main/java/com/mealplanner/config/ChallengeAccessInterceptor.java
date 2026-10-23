package com.mealplanner.config;

import com.mealplanner.model.User;
import com.mealplanner.service.ChallengeService;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.FoodLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Once a client's challenge access ends, they can still sign in and look at everything,
 * but nothing new can be made or saved: no plans, food logs, check-ins or swaps.
 */
@Component
public class ChallengeAccessInterceptor implements HandlerInterceptor {

    static final String ENDED_MESSAGE = "The challenge has ended, so this is read-only now. Talk to your coach to keep going.";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final CurrentUserService currentUserService;
    private final ChallengeService challengeService;
    private final FoodLogService foodLogService;

    public ChallengeAccessInterceptor(CurrentUserService currentUserService, ChallengeService challengeService,
                                      FoodLogService foodLogService) {
        this.currentUserService = currentUserService;
        this.challengeService = challengeService;
        this.foodLogService = foodLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (READ_METHODS.contains(request.getMethod())) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OAuth2User)) return true;
        User user = currentUserService.find((OAuth2User) auth.getPrincipal());
        if (user == null || !challengeService.isReadOnly(user, foodLogService.today(user))) return true;

        // Page forms get sent back to Today with the notice; the page's own fetch calls get a JSON error.
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            response.sendRedirect("/dashboard?ended");
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"" + ENDED_MESSAGE + "\"}");
        }
        return false;
    }
}
