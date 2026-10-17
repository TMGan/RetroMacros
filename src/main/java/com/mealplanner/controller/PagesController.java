package com.mealplanner.controller;

import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.time.format.DateTimeFormatter;

@Controller
public class PagesController {

    private final UserRepository userRepository;
    private final SavedMealPlanRepository savedMealPlanRepository;

    private final com.mealplanner.service.CurrentUserService currentUserService;

    public PagesController(UserRepository userRepository, SavedMealPlanRepository savedMealPlanRepository, com.mealplanner.service.CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.savedMealPlanRepository = savedMealPlanRepository;
    }

    @GetMapping("/about")
    public String about(@AuthenticationPrincipal OAuth2User principal, Model model) {
        // Public page, but signed-in visitors get the signed-in header so they don't look logged out.
        User user = currentUserService.find(principal);
        if (user != null) model.addAttribute("user", user);
        return "about";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal OAuth2User principal, jakarta.servlet.http.HttpSession session, Model model) {
        if (principal == null) return "redirect:/login";
        User user = currentUserService.requireForView(principal, session);

        long totalPlans = savedMealPlanRepository.findByUserOrderByCreatedAtDesc(user).size();
        long daysAsMember = 0;
        if (user.getCreatedAt() != null) {
            daysAsMember = java.time.temporal.ChronoUnit.DAYS.between(
                    user.getCreatedAt().toLocalDate(),
                    java.time.LocalDate.now()
            );
        }
        String memberSince = null;
        String lastLogin = null;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
        if (user.getCreatedAt() != null) {
            memberSince = user.getCreatedAt().format(fmt);
        }
        if (user.getLastLoginAt() != null) {
            lastLogin = user.getLastLoginAt().format(fmt);
        }

        model.addAttribute("user", user);
        model.addAttribute("totalPlans", totalPlans);
        model.addAttribute("daysAsMember", daysAsMember);
        model.addAttribute("memberSince", memberSince != null ? memberSince : "Unknown");
        model.addAttribute("lastLogin", lastLogin);
        return "profile";
    }

    @GetMapping("/my-plans")
    public String myPlans(@AuthenticationPrincipal OAuth2User principal, jakarta.servlet.http.HttpSession session, Model model) {
        if (principal == null) return "redirect:/login";
        User user = currentUserService.requireForView(principal, session);

        List<SavedMealPlan> allPlans = savedMealPlanRepository.findByUserOrderByCreatedAtDesc(user);
        model.addAttribute("user", user);
        model.addAttribute("allPlans", allPlans);
        return "my-plans";
    }
}
