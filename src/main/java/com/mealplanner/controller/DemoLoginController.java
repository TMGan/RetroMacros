package com.mealplanner.controller;

import com.mealplanner.model.User;
import com.mealplanner.service.DemoAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Signs a visitor into the shared demo account without Google.
 *
 * Every controller in this app resolves the current user through
 * {@code @AuthenticationPrincipal OAuth2User}, so the demo session is built as a
 * real {@link OAuth2AuthenticationToken} carrying the same attribute names
 * Google returns (sub, email, name, picture). Nothing downstream needs to know
 * the difference.
 */
@Controller
public class DemoLoginController {

    private final DemoAccountService demoAccountService;
    private final boolean demoEnabled;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public DemoLoginController(DemoAccountService demoAccountService,
                               @Value("${demo.enabled:false}") boolean demoEnabled) {
        this.demoAccountService = demoAccountService;
        this.demoEnabled = demoEnabled;
    }

    @PostMapping("/demo-login")
    public String demoLogin(HttpServletRequest request, HttpServletResponse response) {
        if (!demoEnabled) {
            return "redirect:/login";
        }

        User demo = demoAccountService.resetAndGet();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", demo.getGoogleId());
        attributes.put("email", demo.getEmail());
        attributes.put("name", demo.getName());
        attributes.put("picture", demo.getProfilePictureUrl());

        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        // New session ID on sign-in, so an ID set before login can never be reused after it.
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return "redirect:/dashboard";
    }
}
