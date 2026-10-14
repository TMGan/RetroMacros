package com.mealplanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealplanner.model.FoodLog;
import com.mealplanner.model.GroceryList;
import com.mealplanner.model.MacroTargets;
import com.mealplanner.model.MealPlan;
import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.model.UserProfile;
import com.mealplanner.repository.FoodLogRepository;
import com.mealplanner.repository.LearnedUserPreferenceRepository;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.SwapHistoryRepository;
import com.mealplanner.repository.UserFoodPreferencesRepository;
import com.mealplanner.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Backs the "View demo" button on the login page.
 *
 * The demo account is shared by every visitor, so it is wiped and re-seeded on
 * each demo login. That keeps the account presentable no matter what the
 * previous visitor did to it, and avoids needing a read-only enforcement layer.
 */
@Service
public class DemoAccountService {

    public static final String DEMO_EMAIL = "demo@retromacros.app";
    public static final String DEMO_GOOGLE_ID = "demo-account";
    public static final String DEMO_NAME = "Demo User";
    private static final long RESET_COOLDOWN_SECONDS = 20;

    private volatile Instant lastReset;

    private final UserRepository userRepository;
    private final FoodLogRepository foodLogRepository;
    private final SavedMealPlanRepository savedMealPlanRepository;
    private final SwapHistoryRepository swapHistoryRepository;
    private final LearnedUserPreferenceRepository learnedUserPreferenceRepository;
    private final UserFoodPreferencesRepository userFoodPreferencesRepository;
    private final MealPlanService mealPlanService;
    private final MacroCalculatorService macroCalculatorService;
    private final ObjectMapper mapper = new ObjectMapper();

    public DemoAccountService(UserRepository userRepository,
                              FoodLogRepository foodLogRepository,
                              SavedMealPlanRepository savedMealPlanRepository,
                              SwapHistoryRepository swapHistoryRepository,
                              LearnedUserPreferenceRepository learnedUserPreferenceRepository,
                              UserFoodPreferencesRepository userFoodPreferencesRepository,
                              MealPlanService mealPlanService,
                              MacroCalculatorService macroCalculatorService) {
        this.userRepository = userRepository;
        this.foodLogRepository = foodLogRepository;
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.swapHistoryRepository = swapHistoryRepository;
        this.learnedUserPreferenceRepository = learnedUserPreferenceRepository;
        this.userFoodPreferencesRepository = userFoodPreferencesRepository;
        this.mealPlanService = mealPlanService;
        this.macroCalculatorService = macroCalculatorService;
    }

    /** Returns the demo user, freshly reset to a known-good state. */
    @Transactional
    public User resetAndGet() {
        // The button is public, so do not let rapid clicks hammer the database.
        User existing = userRepository.findByEmail(DEMO_EMAIL).orElse(null);
        Instant now = Instant.now();
        if (existing != null && lastReset != null && now.isBefore(lastReset.plusSeconds(RESET_COOLDOWN_SECONDS))) {
            return existing;
        }
        lastReset = now;

        User demo = existing != null ? existing : new User(DEMO_EMAIL, DEMO_NAME, DEMO_GOOGLE_ID);
        demo.setName(DEMO_NAME);
        demo.setAdmin(false);
        demo.setLastLoginAt(LocalDateTime.now());
        demo.setLastActiveAt(LocalDateTime.now());
        demo = userRepository.save(demo);

        wipe(demo);
        seedFoodLogs(demo);
        seedMealPlan(demo);
        return demo;
    }

    private void wipe(User demo) {
        // Food logs first: they hold an FK to saved meal plans.
        foodLogRepository.deleteAll(foodLogRepository
                .findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(
                        demo, LocalDate.now().minusYears(5), LocalDate.now().plusYears(1)));
        savedMealPlanRepository.deleteAll(savedMealPlanRepository.findByUserOrderByCreatedAtDesc(demo));
        swapHistoryRepository.deleteAll(swapHistoryRepository.findByUserOrderBySwapDateDesc(demo));
        learnedUserPreferenceRepository.deleteAll(
                learnedUserPreferenceRepository.findByUserOrderByConfidenceScoreDesc(demo));
        userFoodPreferencesRepository.findByUser(demo).ifPresent(userFoodPreferencesRepository::delete);
    }

    private void seedFoodLogs(User demo) {
        LocalDate today = LocalDate.now();
        // Three days of history so the dashboard and log history pages have shape.
        log(demo, today, "Breakfast", "3 scrambled eggs, 1 cup oatmeal, 1 banana", 620, 34, 78, 20);
        log(demo, today, "Lunch", "6 oz grilled chicken, 1.5 cups brown rice, broccoli", 710, 58, 82, 14);
        log(demo, today.minusDays(1), "Breakfast", "Greek yogurt, mixed berries, almond butter", 430, 28, 41, 17);
        log(demo, today.minusDays(1), "Lunch", "Turkey sandwich on whole wheat, apple", 560, 38, 66, 15);
        log(demo, today.minusDays(1), "Dinner", "6 oz salmon, sweet potato, mixed vegetables", 680, 46, 58, 28);
        log(demo, today.minusDays(2), "Breakfast", "Protein shake, 2 slices whole grain toast", 480, 42, 52, 11);
        log(demo, today.minusDays(2), "Dinner", "Lean ground beef, jasmine rice, green beans", 720, 52, 74, 22);
    }

    private void log(User demo, LocalDate date, String meal, String description,
                     int calories, int protein, int carbs, int fat) {
        FoodLog entry = new FoodLog();
        entry.setUser(demo);
        entry.setLogDate(date);
        entry.setTimeLogged(date.atTime(12, 0));
        entry.setMealName(meal);
        entry.setFoodDescription(description);
        entry.setCalories(calories);
        entry.setProtein(protein);
        entry.setCarbs(carbs);
        entry.setFat(fat);
        entry.setFromMealPlan(false);
        foodLogRepository.save(entry);
    }

    private void seedMealPlan(User demo) {
        UserProfile profile = new UserProfile();
        profile.setWeight(185);
        profile.setHeightFeet(5);
        profile.setHeightInches(11);
        profile.setAge(28);
        profile.setSex("Male");
        profile.setActivityLevel("Moderately Active");
        profile.setFitnessGoal("Build Muscle");
        profile.setAllergies(List.of());

        double bmr = macroCalculatorService.calculateBMR(
                profile.getWeight(), profile.getHeightFeet(), profile.getHeightInches(),
                profile.getAge(), profile.getSex());
        double tdee = macroCalculatorService.calculateTDEE(bmr, profile.getActivityLevel());
        double goalCalories = macroCalculatorService.adjustForGoal(tdee, profile.getFitnessGoal());
        MacroTargets targets = macroCalculatorService.calculateMacrosByGoal(goalCalories, profile.getFitnessGoal());

        SavedMealPlan saved = new SavedMealPlan();
        saved.setUser(demo);
        saved.setWeight(profile.getWeight());
        saved.setHeightFeet(profile.getHeightFeet());
        saved.setHeightInches(profile.getHeightInches());
        saved.setAge(profile.getAge());
        saved.setSex(profile.getSex());
        saved.setActivityLevel(profile.getActivityLevel());
        saved.setFitnessGoal(profile.getFitnessGoal());
        saved.setTargetCalories(targets.getCalories());
        saved.setTargetProtein(targets.getProtein());
        saved.setTargetCarbs(targets.getCarbs());
        saved.setTargetFat(targets.getFat());

        try {
            MealPlan plan = mealPlanService.generateSampleMealPlan(profile, targets);
            GroceryList groceries = mealPlanService.generateGroceryList(plan);
            saved.setMealPlanJson(mapper.writeValueAsString(plan));
            saved.setGroceryListJson(mapper.writeValueAsString(groceries));
        } catch (Exception ex) {
            // A demo without a sample plan is still usable, so do not fail the login.
            saved.setGenerationFailed(true);
            saved.setErrorMessage("Demo seed could not generate a plan: " + ex.getMessage());
        }
        savedMealPlanRepository.save(saved);
    }
}
