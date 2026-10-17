package com.mealplanner.controller;

import com.mealplanner.model.User;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.service.FoodLogService;
import com.mealplanner.model.FoodLog;
import java.util.List;
import java.util.Map;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.UserFoodPreferencesService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final SavedMealPlanRepository mealPlanRepository;
    private final FoodLogService foodLogService;
    private final UserFoodPreferencesService preferencesService;
    private final com.mealplanner.service.CurrentUserService currentUserService;
    private final com.mealplanner.service.ProgressService progressService;
    private final com.mealplanner.service.MeasurementService measurementService;

    public DashboardController(UserRepository userRepository, SavedMealPlanRepository mealPlanRepository, FoodLogService foodLogService, UserFoodPreferencesService preferencesService, com.mealplanner.service.CurrentUserService currentUserService, com.mealplanner.service.ProgressService progressService, com.mealplanner.service.MeasurementService measurementService) {
        this.measurementService = measurementService;
        this.progressService = progressService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.mealPlanRepository = mealPlanRepository;
        this.foodLogService = foodLogService;
        this.preferencesService = preferencesService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model, HttpSession session) {
        if (principal == null) return "redirect:/login";
        // Accounts are created at sign-in, after the invite check. Never create one here.
        User user = currentUserService.requireForView(principal, session);
        boolean viewingAs = !user.getId().equals(currentUserService.require(principal).getId());
        List<com.mealplanner.model.SavedMealPlan> recent = mealPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        List<FoodLog> todaysFoodLogs = foodLogService.getTodaysFoodLogs(user);
        Map<String, Integer> todaysTotals = new java.util.HashMap<>();
        int tCal = 0, tP = 0, tC = 0, tF = 0;
        for (FoodLog fl : todaysFoodLogs) {
            tCal += fl.getCalories();
            tP += fl.getProtein();
            tC += fl.getCarbs();
            tF += fl.getFat();
        }
        todaysTotals.put("calories", tCal);
        todaysTotals.put("protein", tP);
        todaysTotals.put("carbs", tC);
        todaysTotals.put("fat", tF);
        com.mealplanner.model.SavedMealPlan latestPlan = recent.isEmpty() ? null : recent.get(0);
        // Parse latest plan JSON for Quick Add modal
        com.mealplanner.model.MealPlan latestMealPlan = null;
        if (latestPlan != null && latestPlan.getMealPlanJson() != null) {
            try {
                latestMealPlan = new com.fasterxml.jackson.databind.ObjectMapper().readValue(latestPlan.getMealPlanJson(), com.mealplanner.model.MealPlan.class);
            } catch (Exception ignore) {}
        }

        model.addAttribute("user", user);
        model.addAttribute("savedPlans", recent);
        model.addAttribute("todaysFoodLogs", todaysFoodLogs);
        model.addAttribute("todaysTotals", todaysTotals);
        model.addAttribute("latestPlan", latestPlan);
        model.addAttribute("latestMealPlan", latestMealPlan);
        model.addAttribute("userPreferences", preferencesService.getUserPreferences(user));
        model.addAttribute("impersonating", viewingAs);
        model.addAttribute("checkInDue", !viewingAs && !currentUserService.isDemo(user) && progressService.missedCheckIn(user));
        model.addAttribute("measurementsDue", !viewingAs && !currentUserService.isDemo(user) && measurementService.isDue(user));
        return "dashboard";
    }
}
