package com.mealplanner.config;

import com.mealplanner.model.User;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.UserProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Re-checks access on every request, not just at sign-in. Removing a client's
 * invite, or removing someone from ADMIN_EMAILS, takes effect on their next click.
 */
@Component
public class AccessCheckInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessCheckInterceptor.class);

    private final CurrentUserService currentUserService;
    private final UserProvisioningService userProvisioningService;
    private final UserRepository userRepository;

    public AccessCheckInterceptor(CurrentUserService currentUserService,
                                  UserProvisioningService userProvisioningService,
                                  UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.userProvisioningService = userProvisioningService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!userProvisioningService.isInviteOnly()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OAuth2User)) return true;
        User user = currentUserService.find((OAuth2User) auth.getPrincipal());
        if (user == null || currentUserService.isDemo(user)) return true;

        String email = user.getEmail();
        if (user.isAdmin() && !userProvisioningService.isAdminEmail(email)) {
            user.setAdmin(false);
            userRepository.save(user);
            log.info("Removed admin rights from user {}: no longer in ADMIN_EMAILS", user.getId());
        }
        if (userProvisioningService.isAllowed(email)) return true;

        log.info("Ended session for user {}: no longer invited", user.getId());
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        response.sendRedirect("/login?error=revoked");
        return false;
    }
}
