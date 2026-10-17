package com.mealplanner;

import com.mealplanner.model.*;
import com.mealplanner.repository.AiCallLogRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.AllergenGuard;
import com.mealplanner.service.FoodLogService;
import com.mealplanner.service.MealPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real Anthropic API. Costs real money (a few cents), so it only runs
 * when RUN_LIVE_AI=true and CLAUDE_API_KEY are set:
 *   RUN_LIVE_AI=true ./mvnw test -Dtest=LiveAiCheck
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI", matches = "true")
@SpringBootTest(properties = {"GOOGLE_CLIENT_ID=test", "GOOGLE_CLIENT_SECRET=test", "ai.mock=false"})
class LiveAiCheck {

    @Autowired private MealPlanService mealPlanService;
    @Autowired private FoodLogService foodLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private AiCallLogRepository aiCallLogRepository;

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepository.save(new User(id + "@example.com", "Live Check", "gid-" + id));
    }

    @Test
    void realCallsWorkAndAreCostTracked() {
        User user = newUser();

        long t0 = System.currentTimeMillis();
        Map<String, Integer> estimate = foodLogService.estimateMacrosWithAI(user, "3 scrambled eggs and 2 slices of whole wheat toast", false);
        System.out.println("LIVE estimate: " + estimate + " in " + (System.currentTimeMillis() - t0) + " ms");
        assertThat(estimate).isNotNull();

        t0 = System.currentTimeMillis();
        Map<String, Object> idea = foodLogService.generateRandomHealthyMeal(user, 550, false, List.of("Dairy"), null);
        System.out.println("LIVE random meal (no dairy): " + idea + " in " + (System.currentTimeMillis() - t0) + " ms");
        assertThat(idea).isNotNull();

        MacroTargets targets = new MacroTargets(2200, 180, 220, 70);
        UserProfile profile = new UserProfile(180, 5, 10, 30, "male", "Moderately Active", "Build Muscle", List.of("Fish", "Shellfish"));
        String prefs = "CLIENT'S FOOD PREFERENCES:\n- Foods they like and want: chicken, ground beef, rice, potatoes, eggs\n"
                + "- Foods to avoid: mushrooms\nTreat the foods to avoid as a hard rule, the same as an allergy.\n";
        t0 = System.currentTimeMillis();
        MealPlan plan;
        try {
            plan = mealPlanService.generateMealPlan(user, profile, targets, prefs);
        } finally {
            printCosts();
        }
        long planMs = System.currentTimeMillis() - t0;

        System.out.println("LIVE plan in " + planMs + " ms, allergen hits: " + AllergenGuard.violations(List.of("Fish", "Shellfish", "mushrooms"), plan));
        for (Day day : plan.getDays()) {
            System.out.println("LIVE day " + day.getDayNumber() + " totals " + fmt(day.getDailyTotal()));
            for (Meal meal : day.getMeals()) {
                System.out.println("LIVE   " + meal.getName() + ": " + (meal.getRecipe() != null ? meal.getRecipe().getName() : "") + " " + fmt(meal.getMacros()));
            }
        }
        assertThat(plan.getDays()).hasSize(3);

    }

    private void printCosts() {
        aiCallLogRepository.findAll().forEach(log -> System.out.println("LIVE cost: " + log.getKind() + " " + log.getModel()
                + " in=" + log.getInputTokens() + " out=" + log.getOutputTokens() + " $" + (log.getCostMicros() / 1_000_000.0)
                + " " + log.getDurationMs() + "ms success=" + log.isSuccess() + (log.getErrorType() == null ? "" : " error=" + log.getErrorType())));
        System.out.println("LIVE total cost: $" + (aiCallLogRepository.sumCostMicros() / 1_000_000.0));
    }

    private String fmt(MacroTargets m) {
        return m == null ? "?" : m.getCalories() + " cal / " + m.getProtein() + "P / " + m.getCarbs() + "C / " + m.getFat() + "F";
    }
}
