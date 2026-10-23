package com.mealplanner.controller;

import com.mealplanner.model.User;
import com.mealplanner.service.AiQuotaService;
import com.mealplanner.service.ChallengeService;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.FoodLogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/** The challenge page, plus the challenge info every page can show (week banner, read-only notice). */
@Controller
public class ChallengeController {

    private final ChallengeService challengeService;
    private final CurrentUserService currentUserService;
    private final FoodLogService foodLogService;
    private final AiQuotaService aiQuotaService;

    public ChallengeController(ChallengeService challengeService, CurrentUserService currentUserService,
                               FoodLogService foodLogService, AiQuotaService aiQuotaService) {
        this.challengeService = challengeService;
        this.currentUserService = currentUserService;
        this.foodLogService = foodLogService;
        this.aiQuotaService = aiQuotaService;
    }

    @GetMapping("/challenge")
    public String challenge(@AuthenticationPrincipal OAuth2User principal, HttpSession session, Model model) {
        User user = currentUserService.requireForView(principal, session);
        model.addAttribute("user", user);
        model.addAttribute("plansUsed", aiQuotaService.plansThisWeek(user));
        model.addAttribute("plansPerWeek", aiQuotaService.getPlansPerWeek());
        return "challenge";
    }

    /** Adds {@code challenge} to every page for a signed-in user. */
    @ControllerAdvice
    static class ChallengeModel {

        private final ChallengeService challengeService;
        private final CurrentUserService currentUserService;
        private final FoodLogService foodLogService;

        ChallengeModel(ChallengeService challengeService, CurrentUserService currentUserService, FoodLogService foodLogService) {
            this.challengeService = challengeService;
            this.currentUserService = currentUserService;
            this.foodLogService = foodLogService;
        }

        @ModelAttribute("challenge")
        Map<String, Object> challenge(@AuthenticationPrincipal OAuth2User principal) {
            User user = principal == null ? null : currentUserService.find(principal);
            if (user == null) return null;
            return challengeService.summary(user, foodLogService.today(user));
        }
    }
}
