package com.mealplanner.controller;

import com.mealplanner.model.MacroTargets;
import com.mealplanner.model.Meal;
import com.mealplanner.model.User;
import com.mealplanner.service.AiQuotaService;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.MealPlanService;
import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.service.SwapService;
import com.mealplanner.service.UserFoodPreferencesService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/meal")
public class MealApiController {

    private static final Logger log = LoggerFactory.getLogger(MealApiController.class);
    private static final int MAX_HINT_LENGTH = 120;

    private final MealPlanService mealPlanService;
    private final CurrentUserService currentUserService;
    private final AiQuotaService aiQuotaService;

    private final SavedMealPlanRepository savedMealPlanRepository;
    private final UserFoodPreferencesService preferencesService;
    private final SwapService swapService;

    public MealApiController(MealPlanService mealPlanService,
                             CurrentUserService currentUserService,
                             AiQuotaService aiQuotaService,
                             SavedMealPlanRepository savedMealPlanRepository,
                             UserFoodPreferencesService preferencesService,
                             SwapService swapService) {
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.preferencesService = preferencesService;
        this.swapService = swapService;
        this.mealPlanService = mealPlanService;
        this.currentUserService = currentUserService;
        this.aiQuotaService = aiQuotaService;
    }

    public static class SwapMealRequest {
        public String avoidSimilarTo; // optional hint (e.g., current recipe name)
        public int targetCalories;
        public int targetProtein;
        public int targetCarbs;
        public int targetFat;
    }

    @PostMapping("/swap")
    public ResponseEntity<?> swapMeal(@AuthenticationPrincipal OAuth2User principal,
                                      @RequestBody SwapMealRequest req) {
        User user = currentUserService.require(principal);
        if (req.targetCalories < 0 || req.targetCalories > 3000 || req.targetProtein < 0 || req.targetProtein > 300
                || req.targetCarbs < 0 || req.targetCarbs > 500 || req.targetFat < 0 || req.targetFat > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Macro targets are out of range"));
        }
        if (mealPlanService.isMockMode() || currentUserService.isDemo(user)) {
            return ResponseEntity.status(503).body(Map.of("error", "Meal swap is not available in demo mode"));
        }
        try {
            aiQuotaService.consume(user, AiQuotaService.Kind.SWAP);
            MacroTargets target = new MacroTargets(
                    req.targetCalories,
                    req.targetProtein,
                    req.targetCarbs,
                    req.targetFat
            );
            // Swaps follow the same allergies (from the client's latest plan) and preferences as the plan.
            List<SavedMealPlan> latest = savedMealPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user);
            // Foods the client avoids are kept out of swaps the same way as allergies.
            List<String> allergies = new java.util.ArrayList<>(latest.isEmpty() ? List.of() : latest.get(0).getAllergyList());
            allergies.addAll(preferencesService.avoidedFoods(user));
            String preferences = preferencesService.buildPreferencesPrompt(user) + "\n" + swapService.buildLearnedPreferencesPrompt(user);
            Meal replacement = mealPlanService.generateReplacementMeal(user, target, cleanHint(req.avoidSimilarTo), allergies, preferences);
            if (replacement == null) {
                return ResponseEntity.status(502).body(Map.of("error", "Could not generate replacement meal"));
            }
            return ResponseEntity.ok(replacement);
        } catch (AiQuotaService.QuotaExceededException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (com.mealplanner.service.AiClient.AiException e) {
            // Written for the client: allergy refusals, outages, spend caps.
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Meal swap failed for user {}", user.getId(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Could not generate replacement meal"));
        }
    }

    // The hint is pasted into the AI prompt, so keep it short and single-line.
    private String cleanHint(String hint) {
        if (hint == null) return null;
        String cleaned = hint.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.length() > MAX_HINT_LENGTH ? cleaned.substring(0, MAX_HINT_LENGTH) : cleaned;
    }
}
