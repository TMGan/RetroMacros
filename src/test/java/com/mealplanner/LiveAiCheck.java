package com.mealplanner;

import com.mealplanner.model.*;
import com.mealplanner.repository.AiCallLogRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.AllergenGuard;
import com.mealplanner.service.MacroCalculatorService;
import com.mealplanner.service.MealPlanService;
import com.mealplanner.service.PlanQualityCheck;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real Anthropic API to check whole plans end to end: targets, prompt,
 * code checks and cost. Costs real money (roughly 10 to 20 cents per plan), so it
 * only runs when RUN_LIVE_AI=true. The key comes from CLAUDE_API_KEY or from the
 * git-ignored application-local.properties (ai.api.key=...):
 *   RUN_LIVE_AI=true ./mvnw test -Dtest=LiveAiCheck
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI", matches = "true")
@SpringBootTest(properties = {"GOOGLE_CLIENT_ID=test", "GOOGLE_CLIENT_SECRET=test", "ai.mock=false",
        "spring.config.import=optional:file:./application-local.properties"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveAiCheck {

    @Autowired private MealPlanService mealPlanService;
    @Autowired private MacroCalculatorService macroService;
    @Autowired private com.mealplanner.service.UserFoodPreferencesService preferencesService;
    @Autowired private UserRepository userRepository;
    @Autowired private AiCallLogRepository aiCallLogRepository;

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepository.save(new User(id + "@example.com", "Live Check", "gid-" + id));
    }

    @Test
    void simpleModeFatLoss() {
        UserProfile p = new UserProfile(165, 5, 5, 42, "Female", "Occasionally Active", "Lose Weight", List.of());
        p.setEffortLevel(UserProfile.EFFORT_SIMPLE);
        run("1. Simple mode, fat loss, ramp week 1", p, 1, null);
    }

    @Test
    void dairyAllergyBuildingMuscle() {
        UserProfile p = new UserProfile(175, 5, 11, 29, "Male", "Very Active", "Build Muscle", List.of("Dairy"));
        p.setMacroStyle(UserProfile.STYLE_HIGH_PROTEIN);
        run("2. Dairy allergy, build, higher protein", p, null, null);
    }

    @Test
    void diabetesOnMedication() {
        UserProfile p = new UserProfile(230, 5, 9, 61, "Male", "Sedentary", "Lose Weight", List.of());
        p.setHealthConditions(EnumSet.of(HealthCondition.DIABETES_MEDS));
        p.setHealthNotes("high blood pressure, takes metformin");
        run("3. Health check: diabetes on medication", p, null, null);
    }

    @Test
    void lowerCarbGlutenFreeCook() {
        UserProfile p = new UserProfile(150, 5, 6, 35, "Female", "Moderately Active", "Maintain Weight", List.of("Gluten/Wheat"));
        p.setMacroStyle(UserProfile.STYLE_LOW_CARB);
        p.setEffortLevel(UserProfile.EFFORT_COOK);
        String prefs = "CLIENT'S FOOD PREFERENCES:\n- Foods they like and want: salmon, chicken thighs, sweet potatoes, berries\n"
                + "- Foods to avoid: mushrooms\nTreat the foods to avoid as a hard rule, the same as an allergy.\n";
        run("4. Lower carb, gluten-free, likes to cook", p, null, prefs);
    }

    @Test
    void pickyEverydayClient() {
        // Picks saved the same way the plan form saves them, so the prompt is exactly what a client gets.
        User user = newUser();
        preferencesService.savePreferences(user, "Ground beef, Chicken, Eggs, Rice, Potatoes, Green beans, Bananas",
                "fish", "mostly-home-cooked", "flexible");
        UserProfile p = new UserProfile(205, 5, 10, 52, "Male", "Occasionally Active", "Lose Weight", List.of());
        run("5. Picky everyday client: beef, chicken, eggs, rice, potatoes; no fish", p, null,
                preferencesService.buildPreferencesPrompt(user), user);
    }

    @Test
    void glp1FatLoss() {
        UserProfile p = new UserProfile(210, 5, 4, 58, "Female", "Lightly Active", "Lose Weight", List.of());
        p.setHealthConditions(EnumSet.of(HealthCondition.GLP1));
        run("6. GLP-1 medication, fat loss", p, null, null);
    }

    @Test
    void diabetesAndGlp1Cleared() {
        // Like the client whose plans kept getting cut off: both conditions, cleared by the coach.
        UserProfile p = new UserProfile(212, 5, 10, 55, "Male", "Lightly Active", "Lose Weight", List.of());
        p.setHealthConditions(EnumSet.of(HealthCondition.DIABETES_MEDS, HealthCondition.GLP1));
        p.setHealthCleared(true);
        run("7. Diabetes + GLP-1, cleared, fat loss", p, null, null);
    }

    @Test
    void shortFiftyYearOldWomanFatLoss() {
        // The client who got 1,883 cal / 77 g protein / 252 g carbs before the ramp was switched off.
        UserProfile p = new UserProfile(159, 5, 1, 50, "Female", "Occasionally Active", "Lose Weight", List.of());
        p.setGoalWeight(130.0);
        run("9. 5'1\" woman, 159 lb, age 50, fat loss", p, null, null);
    }

    @Test
    void diabetesGlp1NoSalmon() {
        // The same client's second report: salmon showed up although they avoid it.
        User user = newUser();
        preferencesService.savePreferences(user, "Chicken, Eggs, Rice, Broccoli", "No salmon", "mostly-home-cooked", "flexible");
        UserProfile p = new UserProfile(205, 5, 10, 65, "Male", "Occasionally Active", "Lose Weight", List.of());
        p.setHealthConditions(EnumSet.of(HealthCondition.DIABETES_MEDS, HealthCondition.GLP1));
        p.setHealthCleared(true);
        p.setAvoidedFoods(preferencesService.avoidedFoods(user));
        run("8. Diabetes + GLP-1, cleared, no salmon", p, null, preferencesService.buildPreferencesPrompt(user), user);
    }

    private void run(String title, UserProfile profile, Integer rampWeek, String prefs) {
        run(title, profile, rampWeek, prefs, newUser());
    }

    private void run(String title, UserProfile profile, Integer rampWeek, String prefs, User user) {
        NutritionTargets nutrition = macroService.buildTargets(profile, rampWeek);
        MacroTargets targets = nutrition.toMacroTargets();
        StringBuilder out = new StringBuilder("\n===== LIVE " + title + " =====\n");
        out.append("Targets: ").append(fmt(targets)).append(" fiber ").append(targets.getFiber())
           .append("g (maintenance ").append(nutrition.getMaintenanceCalories()).append(", hold=").append(nutrition.isHealthHold()).append(")\n");

        long t0 = System.currentTimeMillis();
        MealPlan plan;
        try {
            plan = mealPlanService.generateMealPlan(user, profile, targets, prefs);
        } catch (RuntimeException ex) {
            System.out.println(out + "FAILED: " + ex.getMessage() + "\n" + costs(user));
            throw ex;
        }
        out.append("Built in ").append((System.currentTimeMillis() - t0) / 1000).append("s\n");

        List<String> avoid = new java.util.ArrayList<>(profile.getAllergies());
        avoid.addAll(profile.getAvoidedFoods());
        if (prefs != null && prefs.contains("mushrooms")) avoid.add("mushrooms");
        if (prefs != null && prefs.contains("Foods to avoid: fish")) avoid.add("fish");
        out.append("Allergen hits: ").append(AllergenGuard.violations(avoid, plan)).append('\n');
        out.append("Problems after checks: ").append(PlanQualityCheck.problems(plan, targets)).append('\n');
        out.append("Accuracy score: ").append(PlanQualityCheck.accuracyScore(plan, targets)).append('\n');
        for (Day day : plan.getDays()) {
            out.append("Day ").append(day.getDayNumber()).append(": ").append(fmt(day.getDailyTotal()))
               .append(" fiber ").append(day.getDailyTotal().getFiber()).append('\n');
            for (Meal meal : day.getMeals()) {
                out.append("   [").append(meal.getType()).append("] ").append(meal.getName()).append(" (").append(fmt(meal.getMacros())).append(")\n      ");
                meal.getFoods().forEach(f -> out.append(f.getPortion()).append(' ').append(f.getItem()).append("; "));
                out.append('\n');
            }
        }
        out.append(costs(user));
        System.out.println(out);

        assertThat(AllergenGuard.violations(avoid, plan)).isEmpty();
        assertThat(plan.getDays()).hasSize(3);
    }

    private String costs(User user) {
        StringBuilder sb = new StringBuilder();
        aiCallLogRepository.findAll().stream().filter(l -> l.getUser() != null && l.getUser().getId().equals(user.getId()))
                .forEach(l -> sb.append("Call: ").append(l.getKind()).append(' ').append(l.getModel())
                        .append(" in=").append(l.getInputTokens()).append(" out=").append(l.getOutputTokens())
                        .append(" $").append(l.getCostMicros() / 1_000_000.0).append(' ').append(l.getDurationMs() / 1000).append("s")
                        .append(l.isSuccess() ? "" : " FAILED " + l.getErrorType()).append('\n'));
        sb.append("Plan cost: $").append(aiCallLogRepository.sumCostMicrosForUser(user) / 1_000_000.0).append('\n');
        return sb.toString();
    }

    @AfterAll
    void totalCost() {
        System.out.println("\n===== LIVE total cost of this run: $" + aiCallLogRepository.sumCostMicros() / 1_000_000.0 + " =====");
    }

    private String fmt(MacroTargets m) {
        return m == null ? "?" : m.getCalories() + " cal / " + m.getProtein() + "P / " + m.getCarbs() + "C / " + m.getFat() + "F";
    }
}
