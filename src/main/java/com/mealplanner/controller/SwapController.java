package com.mealplanner.controller;

import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.model.UserFoodPreferences;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.service.AiQuotaService;
import com.mealplanner.service.AllergenGuard;
import com.mealplanner.service.UserFoodPreferencesService;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.SwapService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/swap")
public class SwapController {

    // Recorded swaps feed future AI prompts, so their text is short and their types fixed.
    private static final Set<String> SWAP_TYPES = Set.of("ingredient", "meal");
    private static final int MAX_FOOD_LENGTH = 120;
    private static final int MAX_CONTEXT_LENGTH = 50;

    private final SwapService swapService;
    private final CurrentUserService currentUserService;
    private final AiQuotaService aiQuotaService;

    private final SavedMealPlanRepository savedMealPlanRepository;
    private final UserFoodPreferencesService preferencesService;

    public SwapController(SwapService swapService, CurrentUserService currentUserService, AiQuotaService aiQuotaService,
                          SavedMealPlanRepository savedMealPlanRepository, UserFoodPreferencesService preferencesService) {
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.preferencesService = preferencesService;
        this.swapService = swapService;
        this.currentUserService = currentUserService;
        this.aiQuotaService = aiQuotaService;
    }

    @PostMapping("/ingredient-options")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getIngredientSwapOptions(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam String originalFood,
            @RequestParam int calories,
            @RequestParam int protein,
            @RequestParam int carbs,
            @RequestParam int fat) {
        User user = currentUserService.require(principal);
        Map<String, Integer> macros = Map.of(
                "calories", calories,
                "protein", protein,
                "carbs", carbs,
                "fat", fat
        );
        // The swap lists are fixed, so drop anything the client is allergic to or asked to avoid,
        // using the same check the meal plans go through.
        List<String> mustAvoid = new java.util.ArrayList<>();
        List<SavedMealPlan> latest = savedMealPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        if (!latest.isEmpty()) mustAvoid.addAll(latest.get(0).getAllergyList());
        UserFoodPreferences prefs = preferencesService.getUserPreferences(user);
        if (prefs != null && prefs.getAvoidedFoodsText() != null) {
            for (String food : prefs.getAvoidedFoodsText().split(",")) {
                if (!food.isBlank()) mustAvoid.add(food.trim());
            }
        }
        List<Map<String, Object>> suggestions = swapService.getSwapSuggestions(originalFood, macros).stream()
                .filter(s -> AllergenGuard.violations(mustAvoid, (String) s.get("name"), List.of()).isEmpty())
                .toList();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("suggestions", suggestions);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/record")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recordSwap(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam String swapType,
            @RequestParam String originalFood,
            @RequestParam String replacementFood,
            @RequestParam String mealContext) {
        User user = currentUserService.require(principal);
        String original = clean(originalFood, MAX_FOOD_LENGTH);
        String replacement = clean(replacementFood, MAX_FOOD_LENGTH);
        if (!SWAP_TYPES.contains(swapType) || original.isEmpty() || replacement.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        try {
            aiQuotaService.consume(user, AiQuotaService.Kind.SWAP_RECORD);
        } catch (AiQuotaService.QuotaExceededException e) {
            return ResponseEntity.status(429).body(Map.of("success", false, "error", e.getMessage()));
        }
        swapService.recordSwap(user, swapType, original, replacement, clean(mealContext, MAX_CONTEXT_LENGTH));
        return ResponseEntity.ok(Map.of("success", true, "message", "Swap recorded"));
    }

    private String clean(String value, int max) {
        if (value == null) return "";
        String s = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
