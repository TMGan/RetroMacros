package com.mealplanner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealplanner.model.GroceryList;
import com.mealplanner.model.MacroTargets;
import com.mealplanner.model.MealPlan;
import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

@Controller
public class PlanController {

    private final SavedMealPlanRepository savedMealPlanRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    private final com.mealplanner.service.CurrentUserService currentUserService;

    public PlanController(SavedMealPlanRepository savedMealPlanRepository, UserRepository userRepository, com.mealplanner.service.CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/plan/{id}")
    public String viewPlan(@PathVariable Long id,
                           @AuthenticationPrincipal OAuth2User principal,
                           jakarta.servlet.http.HttpSession session,
                           Model model) {
        if (principal == null) return "redirect:/login";
        User user = currentUserService.requireForView(principal, session);

        Optional<SavedMealPlan> opt = savedMealPlanRepository.findById(id);
        if (opt.isEmpty()) return "redirect:/dashboard";
        SavedMealPlan saved = opt.get();
        if (saved.getUser() == null || !saved.getUser().getId().equals(user.getId())) {
            return "redirect:/dashboard";
        }

        try {
            MealPlan mealPlan = mapper.readValue(saved.getMealPlanJson(), MealPlan.class);
            GroceryList groceryList = mapper.readValue(saved.getGroceryListJson(), GroceryList.class);
            com.mealplanner.model.NutritionTargets nutrition = com.mealplanner.model.NutritionTargets.fromSaved(saved);
            MacroTargets targets = nutrition.toMacroTargets();

            model.addAttribute("targets", targets);
            model.addAttribute("nutrition", nutrition);
            model.addAttribute("mealPlan", mealPlan);
            model.addAttribute("groceryList", groceryList);
            return "results"; // reuse results template for display
        } catch (Exception e) {
            return "redirect:/dashboard";
        }
    }
}

