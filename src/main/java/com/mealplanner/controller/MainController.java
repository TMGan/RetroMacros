package com.mealplanner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealplanner.model.*;
import com.mealplanner.repository.BodyCheckInRepository;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class MainController {

    private final MacroCalculatorService macroService;
    private final MealPlanService mealPlanService;
    private final UserRepository userRepository;
    private final SavedMealPlanRepository savedMealPlanRepository;
    private final BodyCheckInRepository checkInRepository;
    private final UserFoodPreferencesService preferencesService;
    private final SwapService swapService;
    private final CurrentUserService currentUserService;
    private final AiQuotaService aiQuotaService;
    private final FoodLogService foodLogService;
    private final WeeklyAdjustmentService weeklyAdjustmentService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MainController.class);
    private static final int MAX_OTHER_ALLERGIES_LENGTH = 200;
    // Health notes go into the AI prompt, so they are bounded like other client text.
    private static final int MAX_HEALTH_NOTES_LENGTH = 300;

    public MainController(MacroCalculatorService macroService,
                          MealPlanService mealPlanService,
                          UserRepository userRepository,
                          SavedMealPlanRepository savedMealPlanRepository,
                          BodyCheckInRepository checkInRepository,
                          UserFoodPreferencesService preferencesService,
                          SwapService swapService,
                          CurrentUserService currentUserService,
                          AiQuotaService aiQuotaService,
                          FoodLogService foodLogService,
                          WeeklyAdjustmentService weeklyAdjustmentService) {
        this.macroService = macroService;
        this.mealPlanService = mealPlanService;
        this.userRepository = userRepository;
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.checkInRepository = checkInRepository;
        this.preferencesService = preferencesService;
        this.swapService = swapService;
        this.currentUserService = currentUserService;
        this.aiQuotaService = aiQuotaService;
        this.foodLogService = foodLogService;
        this.weeklyAdjustmentService = weeklyAdjustmentService;
    }

    @GetMapping("/")
    public String root(org.springframework.security.core.Authentication authentication) {
        if (authentication != null && (authentication.getPrincipal() instanceof OAuth2User)) {
            return "redirect:/dashboard";
        }
        return "redirect:/home";
    }

    @GetMapping("/form")
    public String form(@AuthenticationPrincipal OAuth2User principal, Model model) {
        User user = currentUserService.require(principal);
        addFormModel(model, user, prefillFromHistory(user));
        return "form"; // templates/form.html
    }

    @PostMapping("/generate")
    public String generate(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam double weight,
            @RequestParam int heightFeet,
            @RequestParam int heightInches,
            @RequestParam int age,
            @RequestParam String sex,
            @RequestParam String activityLevel,
            @RequestParam String fitnessGoal,
            @RequestParam(name = "bmrFormula", required = false, defaultValue = "mifflin-st-jeor") String bmrFormula,
            @RequestParam(name = "bodyFatPercentage", required = false) Double bodyFatPercentage,
            @RequestParam(name = "goalWeight", required = false) Double goalWeight,
            @RequestParam(name = "macroStyle", required = false, defaultValue = UserProfile.STYLE_BALANCED) String macroStyle,
            @RequestParam(name = "proteinPercent", required = false, defaultValue = "30") int proteinPercent,
            @RequestParam(name = "carbsPercent", required = false, defaultValue = "40") int carbsPercent,
            @RequestParam(name = "fatPercent", required = false, defaultValue = "30") int fatPercent,
            @RequestParam(name = "effortLevel", required = false, defaultValue = UserProfile.EFFORT_STANDARD) String effortLevel,
            @RequestParam(name = "ramp", required = false, defaultValue = "false") boolean ramp,
            @RequestParam(name = "healthConditions", required = false) List<String> healthConditions,
            @RequestParam(name = "healthNotes", required = false) String healthNotes,
            @RequestParam(name = "acceptDisclaimer", required = false, defaultValue = "false") boolean acceptDisclaimer,
            @RequestParam(required = false) List<String> allergies,
            @RequestParam(required = false, name = "otherAllergies") String otherAllergies,
            @RequestParam(required = false, name = "likedFoods") List<String> likedFoods,
            @RequestParam(required = false, name = "otherLikes") String otherLikes,
            @RequestParam(required = false, name = "dislikes") String dislikes,
            @RequestParam(required = false, name = "dietStyle") String dietStyle,
            Model model
    ) {
        User user = currentUserService.require(principal);

        Set<HealthCondition> health = EnumSet.noneOf(HealthCondition.class);
        if (healthConditions != null) healthConditions.forEach(k -> HealthCondition.fromKey(k).ifPresent(health::add));
        String notes = bound(healthNotes, MAX_HEALTH_NOTES_LENGTH);

        // What the form shows again if something is wrong.
        Map<String, Object> submitted = new HashMap<>();
        // Whole pounds show as "182", not "182.0".
        submitted.put("weight", weight == Math.rint(weight) ? (Object) Math.round(weight) : weight);
        submitted.put("heightFeet", heightFeet);
        submitted.put("heightInches", heightInches);
        submitted.put("age", age);
        submitted.put("sex", sex);
        submitted.put("activityLevel", activityLevel);
        submitted.put("fitnessGoal", fitnessGoal);
        submitted.put("bmrFormula", bmrFormula);
        submitted.put("bodyFat", bodyFatPercentage);
        submitted.put("goalWeight", goalWeight);
        submitted.put("macroStyle", macroStyle);
        submitted.put("proteinPercent", proteinPercent);
        submitted.put("carbsPercent", carbsPercent);
        submitted.put("fatPercent", fatPercent);
        submitted.put("effortLevel", effortLevel);
        submitted.put("ramp", ramp);
        submitted.put("healthConditions", health.stream().map(HealthCondition::getKey).toList());
        submitted.put("healthNotes", notes);
        submitted.put("allergies", allergies == null ? List.of() : allergies);
        submitted.put("otherAllergies", otherAllergies);
        submitted.put("likedFoods", likedFoods == null ? List.of() : likedFoods);
        submitted.put("otherLikes", otherLikes);
        submitted.put("dislikes", dislikes);
        submitted.put("dietStyle", dietStyle == null ? "flexible" : dietStyle);

        List<String> errors = validateInputs(weight, heightFeet, heightInches, age, sex, activityLevel, fitnessGoal);
        if (bodyFatPercentage != null && (bodyFatPercentage < 3 || bodyFatPercentage > 70)) {
            errors.add("Body fat must be between 3% and 70%.");
        }
        if (goalWeight != null && (goalWeight.isNaN() || goalWeight < 70 || goalWeight > 700)) {
            errors.add("Goal weight must be between 70 and 700 lb.");
        }
        if (!UserProfile.MACRO_STYLES.contains(macroStyle)) errors.add("Pick a macro style.");
        if (!UserProfile.EFFORT_LEVELS.contains(effortLevel)) errors.add("Pick an effort level.");
        if (UserProfile.STYLE_CUSTOM.equals(macroStyle)) {
            if (proteinPercent + carbsPercent + fatPercent != 100) {
                errors.add("Custom macro percentages must add up to 100%.");
            } else if (proteinPercent < 20 || proteinPercent > 50 || carbsPercent < 20 || carbsPercent > 60
                    || fatPercent < 20 || fatPercent > 45) {
                errors.add("Custom split: protein 20-50%, carbs 20-60%, fat 20-45%.");
            }
        }
        if (user.getDisclaimerAcceptedAt() == null && !acceptDisclaimer) {
            errors.add("Please read and accept the disclaimer before building a plan.");
        }
        if (!errors.isEmpty()) return formWithErrors(model, user, submitted, errors);

        List<String> allAllergies = new ArrayList<>();
        // Only the known checkbox values; free text goes through the capped "other" box.
        if (allergies != null) allergies.stream().filter(allergyOptions()::contains).distinct().forEach(allAllergies::add);
        if (StringUtils.hasText(otherAllergies)) {
            // Free text ends up in the AI prompt, so bound it.
            Arrays.stream(bound(otherAllergies, MAX_OTHER_ALLERGIES_LENGTH).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(allAllergies::add);
        }

        // The food picks become the client's saved preferences, which the plan prompt is built around.
        saveFoodPicks(user, likedFoods, otherLikes, dislikes, dietStyle);

        // Remember the health screen. A changed answer needs the coach to clear it again.
        String healthKeys = HealthCondition.join(health);
        if (!healthKeys.equals(Objects.toString(user.getHealthConditions(), ""))) user.setHealthCleared(false);
        user.setHealthConditions(healthKeys.isEmpty() ? null : healthKeys);
        user.setHealthNotes(notes == null || notes.isBlank() ? null : notes);
        if (user.getDisclaimerAcceptedAt() == null) user.setDisclaimerAcceptedAt(LocalDateTime.now());
        // The form shows the current goal, so an empty box means the client cleared it.
        user.setGoalWeightLbs(goalWeight == null ? null : Math.round(goalWeight * 10) / 10.0);

        LocalDate today = foodLogService.today(user);
        Integer rampWeek = null;
        if (ramp) {
            if (user.getRampStartDate() == null) user.setRampStartDate(today);
            rampWeek = MacroCalculatorService.rampWeek(user.getRampStartDate(), today);
        } else {
            user.setRampStartDate(null);
        }
        // Targets change once a week: a second plan in the same week keeps the first one's numbers,
        // and the weigh-in adjustment only runs with the first plan of a new week.
        SavedMealPlan weekPlan = firstPlanThisWeek(user, today, fitnessGoal, macroStyle, healthKeys);
        int weeklyChange = weekPlan == null
                ? weeklyAdjustmentService.update(user, fitnessGoal, today, rampWeek != null, health)
                : 0;
        userRepository.save(user);

        UserProfile profile = new UserProfile(weight, heightFeet, heightInches, age,
                sex, activityLevel, fitnessGoal, allAllergies);
        profile.setBmrFormula(bmrFormula);
        profile.setBodyFatPercentage(bodyFatPercentage);
        profile.setGoalWeight(user.getGoalWeightLbs());
        profile.setMacroStyle(macroStyle);
        profile.setCustomProteinPercent(proteinPercent);
        profile.setCustomCarbsPercent(carbsPercent);
        profile.setCustomFatPercent(fatPercent);
        profile.setEffortLevel(effortLevel);
        profile.setHealthConditions(health);
        profile.setHealthNotes(user.getHealthNotes());
        profile.setHealthCleared(user.isHealthCleared());
        profile.setCalorieAdjustment(user.getCalorieAdjustment());

        NutritionTargets nutrition;
        try {
            nutrition = macroService.buildTargets(profile, rampWeek);
        } catch (IllegalArgumentException iae) {
            return formWithErrors(model, user, submitted, List.of(iae.getMessage()));
        }
        if (weekPlan != null) keepWeekTargets(nutrition, weekPlan);
        MacroTargets targets = nutrition.toMacroTargets();

        // A plan that fails to build hands its uses back, so a hiccup never costs one of the client's weekly plans.
        boolean saveCounted = false;
        boolean planCounted = false;
        try {
            // Every plan is saved, so cap saves for every account, including the public demo.
            aiQuotaService.consume(user, AiQuotaService.Kind.PLAN_SAVE);
            saveCounted = true;
            MealPlan mealPlan;
            if (currentUserService.isDemo(user)) {
                // The shared demo account never spends API tokens.
                mealPlan = mealPlanService.generateSampleMealPlan(profile, targets);
            } else {
                aiQuotaService.consume(user, AiQuotaService.Kind.PLAN);
                planCounted = true;
                String preferencesExtra = preferencesService.buildPreferencesPrompt(user);
                String learnedExtra = swapService.buildLearnedPreferencesPrompt(user);
                String combinedExtra = (preferencesExtra == null ? "" : preferencesExtra) + "\n" + (learnedExtra == null ? "" : learnedExtra);
                mealPlan = mealPlanService.generateMealPlan(user, profile, targets, combinedExtra.isBlank() ? null : combinedExtra);
            }
            GroceryList groceryList = mealPlanService.generateGroceryList(mealPlan);

            SavedMealPlan saved = new SavedMealPlan();
            saved.setUser(user);
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
            saved.setAllergies(String.join(", ", allAllergies));
            saved.setMacroStyle(macroStyle);
            saved.setEffortLevel(effortLevel);
            saved.setRampWeek(nutrition.getRampWeek());
            saved.setFiberTarget(nutrition.getFiber());
            saved.setWaterOz(nutrition.getWaterOz());
            saved.setHealthHold(nutrition.isHealthHold());
            saved.setHealthConditions(healthKeys.isEmpty() ? null : healthKeys);
            // What the plan really adds up to, so the coach's accuracy numbers are real.
            MacroTargets actual = PlanQualityCheck.averageDailyTotals(mealPlan);
            if (actual != null) {
                saved.setActualCalories(actual.getCalories());
                saved.setActualProtein(actual.getProtein());
                saved.setActualCarbs(actual.getCarbs());
                saved.setActualFat(actual.getFat());
            }
            saved.setAccuracyScore(PlanQualityCheck.accuracyScore(mealPlan, targets));
            if (!mealPlan.getQualityNotes().isEmpty()) saved.setQualityNotes(String.join("\n", mealPlan.getQualityNotes()));
            try {
                ObjectMapper mapper = new ObjectMapper();
                saved.setMealPlanJson(mapper.writeValueAsString(mealPlan));
                saved.setGroceryListJson(mapper.writeValueAsString(groceryList));
            } catch (Exception ignored) {}
            savedMealPlanRepository.save(saved);

            model.addAttribute("targets", targets);
            model.addAttribute("nutrition", nutrition);
            model.addAttribute("mealPlan", mealPlan);
            model.addAttribute("groceryList", groceryList);
            model.addAttribute("weeklyChange", weeklyChange);
            return "results"; // templates/results.html
        } catch (AiQuotaService.QuotaExceededException ex) {
            refundUses(user, saveCounted, planCounted);
            return formWithErrors(model, user, submitted, List.of(ex.getMessage()));
        } catch (AiClient.AiException ex) {
            refundUses(user, saveCounted, planCounted);
            // These messages are written to be shown to clients (allergy refusals, outages, spend limits).
            log.warn("Meal plan generation failed for user {}: {}", user.getId(), ex.getMessage());
            return formWithErrors(model, user, submitted, List.of(ex.getMessage()));
        } catch (Exception ex) {
            refundUses(user, saveCounted, planCounted);
            // Details stay in the server log; they can include raw provider responses.
            log.warn("Meal plan generation failed for user {}", user.getId(), ex);
            return formWithErrors(model, user, submitted, List.of("We couldn't generate your meal plan right now. Please try again in a minute."));
        }
    }

    /**
     * The client's first plan this week (Monday to Sunday) with the same goal, macro style and
     * health answers, or null. Changing any of those gets fresh targets mid-week.
     */
    private SavedMealPlan firstPlanThisWeek(User user, LocalDate today, String goal, String macroStyle, String healthKeys) {
        if (UserProfile.STYLE_CUSTOM.equals(macroStyle)) return null;
        LocalDate weekStart = ChallengeService.weekStart(today);
        SavedMealPlan first = null;
        for (SavedMealPlan p : savedMealPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user)) {
            if (p.getCreatedAt() == null || p.getCreatedAt().toLocalDate().isBefore(weekStart)) break;
            if (!p.isGenerationFailed()
                    && Objects.equals(p.getFitnessGoal(), goal)
                    && Objects.equals(p.getMacroStyle(), macroStyle)
                    && Objects.toString(p.getHealthConditions(), "").equals(healthKeys)) {
                first = p;
            }
        }
        return first;
    }

    private static void keepWeekTargets(NutritionTargets nutrition, SavedMealPlan weekPlan) {
        nutrition.setCalories(weekPlan.getTargetCalories());
        nutrition.setProtein(weekPlan.getTargetProtein());
        nutrition.setCarbs(weekPlan.getTargetCarbs());
        nutrition.setFat(weekPlan.getTargetFat());
        if (weekPlan.getFiberTarget() != null) nutrition.setFiber(weekPlan.getFiberTarget());
        nutrition.setRampWeek(weekPlan.getRampWeek());
    }

    private void refundUses(User user, boolean saveCounted, boolean planCounted) {
        if (saveCounted) aiQuotaService.refund(user, AiQuotaService.Kind.PLAN_SAVE);
        if (planCounted) aiQuotaService.refund(user, AiQuotaService.Kind.PLAN);
    }

    private String formWithErrors(Model model, User user, Map<String, Object> submitted, List<String> errors) {
        model.addAttribute("errors", errors);
        addFormModel(model, user, submitted);
        return "form";
    }

    private void addFormModel(Model model, User user, Map<String, Object> prefill) {
        model.addAttribute("prefill", prefill);
        model.addAttribute("activityLevels", activityLevels());
        model.addAttribute("goals", goals());
        model.addAttribute("allergyOptions", allergyOptions());
        model.addAttribute("foodOptions", FOOD_OPTIONS);
        model.addAttribute("dietOptions", DIET_OPTIONS);
        model.addAttribute("healthOptions", HealthCondition.values());
        model.addAttribute("needsDisclaimer", user.getDisclaimerAcceptedAt() == null);
        model.addAttribute("rampWeekNow", MacroCalculatorService.rampWeek(user.getRampStartDate(), foodLogService.today(user)));
    }

    /** Starts the form from the last plan and the latest weigh-in, so targets follow the client's progress. */
    private Map<String, Object> prefillFromHistory(User user) {
        Map<String, Object> p = new HashMap<>();
        // The template reads every key, and a missing one is an error, so start them all as empty.
        for (String key : List.of("weight", "heightFeet", "heightInches", "age", "sex", "activityLevel", "fitnessGoal",
                "bodyFat", "otherAllergies")) {
            p.put(key, null);
        }
        p.put("goalWeight", user.getGoalWeightLbs());
        p.put("macroStyle", UserProfile.STYLE_BALANCED);
        p.put("effortLevel", UserProfile.EFFORT_STANDARD);
        p.put("proteinPercent", 30);
        p.put("carbsPercent", 40);
        p.put("fatPercent", 30);
        p.put("bmrFormula", "mifflin-st-jeor");
        p.put("ramp", user.getRampStartDate() != null);
        p.put("healthConditions", HealthCondition.parse(user.getHealthConditions()).stream().map(HealthCondition::getKey).toList());
        p.put("healthNotes", user.getHealthNotes());
        p.put("allergies", List.of());

        List<SavedMealPlan> plans = savedMealPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        if (!plans.isEmpty()) {
            SavedMealPlan last = plans.get(0);
            p.put("weight", Math.round(last.getWeight()));
            p.put("heightFeet", last.getHeightFeet());
            p.put("heightInches", last.getHeightInches());
            p.put("age", last.getAge());
            p.put("sex", last.getSex());
            p.put("activityLevel", last.getActivityLevel());
            p.put("fitnessGoal", last.getFitnessGoal());
            if (last.getMacroStyle() != null) p.put("macroStyle", last.getMacroStyle());
            if (last.getEffortLevel() != null) p.put("effortLevel", last.getEffortLevel());
            List<String> known = new ArrayList<>();
            List<String> other = new ArrayList<>();
            for (String a : last.getAllergyList()) (allergyOptions().contains(a) ? known : other).add(a);
            p.put("allergies", known);
            p.put("otherAllergies", String.join(", ", other));
        }
        checkInRepository.findTopByUserOrderByCheckInDateDesc(user)
                .ifPresent(c -> p.put("weight", Math.round(c.getWeightLbs())));
        prefillFoodPicks(p, user);
        return p;
    }

    private static String bound(String text, int max) {
        if (text == null) return null;
        String s = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private List<String> validateInputs(double weight, int feet, int inches, int age, String sex,
                                        String activity, String goal) {
        List<String> errs = new ArrayList<>();
        if (weight < 50 || weight > 500) errs.add("Weight must be between 50 and 500 lbs.");
        if (feet < 3 || feet > 8) errs.add("Height feet must be between 3 and 8.");
        if (inches < 0 || inches > 11) errs.add("Height inches must be between 0 and 11.");
        if (age < 18 || age > 100) errs.add("Age must be between 18 and 100.");
        if (!List.of("male", "female").contains(Optional.ofNullable(sex).orElse("").toLowerCase())) {
            errs.add("Sex must be Male or Female.");
        }
        if (!activityLevels().contains(activity)) errs.add("Invalid activity level.");
        if (!goals().contains(goal)) errs.add("Invalid goal.");
        return errs;
    }

    private List<String> activityLevels() {
        return List.of("Sedentary", "Occasionally Active", "Moderately Active", "Very Active");
    }

    private List<String> goals() {
        return List.of("Lose Weight", "Maintain Weight", "Build Muscle");
    }

    private List<String> allergyOptions() {
        return List.of("Dairy", "Eggs", "Peanuts", "Tree Nuts", "Soy", "Shellfish", "Fish", "Gluten/Wheat");
    }

    /** Everyday foods clients can tap on the plan form. Plain, common versions on purpose. */
    static final Map<String, List<String>> FOOD_OPTIONS;
    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Proteins", List.of("Chicken", "Ground beef", "Ground turkey", "Steak", "Pork", "Salmon", "Tuna", "Shrimp",
                "Eggs", "Greek yogurt", "Cottage cheese", "Beans and lentils", "Tofu", "Protein shakes"));
        m.put("Carbs", List.of("Rice", "Potatoes", "Sweet potatoes", "Oatmeal", "Pasta", "Bread", "Tortillas", "Quinoa"));
        m.put("Vegetables", List.of("Broccoli", "Green beans", "Salad greens", "Spinach", "Peppers", "Carrots",
                "Zucchini", "Asparagus", "Frozen mixed vegetables"));
        m.put("Fruit", List.of("Bananas", "Berries", "Apples", "Oranges", "Grapes"));
        FOOD_OPTIONS = Collections.unmodifiableMap(m);
    }
    static final List<String> ALL_FOOD_OPTIONS = FOOD_OPTIONS.values().stream().flatMap(List::stream).toList();
    /** Diet choices on the form: stored value, label. */
    static final List<String[]> DIET_OPTIONS = List.of(
            new String[]{"flexible", "No restrictions"},
            new String[]{"vegetarian", "Vegetarian"},
            new String[]{"vegan", "Vegan"},
            new String[]{"pescatarian", "Pescatarian"});
    private static final int MAX_FOOD_TEXT_LENGTH = 200;

    /** Saves the form's food picks as the client's preferences, which every plan and swap prompt reads. */
    private void saveFoodPicks(User user, List<String> likedFoods, String otherLikes, String dislikes, String dietStyle) {
        List<String> liked = new ArrayList<>();
        if (likedFoods != null) likedFoods.stream().filter(ALL_FOOD_OPTIONS::contains).distinct().forEach(liked::add);
        String other = bound(otherLikes, MAX_FOOD_TEXT_LENGTH);
        if (other != null && !other.isBlank()) liked.add(other);
        UserFoodPreferences existing = preferencesService.getUserPreferences(user);
        String cooking = existing != null && existing.getCookingPreference() != null ? existing.getCookingPreference() : "mostly-home-cooked";
        boolean knownDiet = DIET_OPTIONS.stream().anyMatch(d -> d[0].equals(dietStyle));
        String diet = knownDiet ? dietStyle : (existing != null && existing.getDietaryStyle() != null ? existing.getDietaryStyle() : "flexible");
        preferencesService.savePreferences(user, String.join(", ", liked), bound(dislikes, MAX_FOOD_TEXT_LENGTH), cooking, diet);
    }

    /** Splits saved preferences back into the form's pills and free-text boxes. */
    private void prefillFoodPicks(Map<String, Object> p, User user) {
        List<String> liked = new ArrayList<>();
        List<String> other = new ArrayList<>();
        UserFoodPreferences prefs = preferencesService.getUserPreferences(user);
        if (prefs != null && prefs.getPreferredFoodsText() != null) {
            for (String item : prefs.getPreferredFoodsText().split(",")) {
                String food = item.trim();
                if (food.isEmpty()) continue;
                String match = ALL_FOOD_OPTIONS.stream().filter(o -> o.equalsIgnoreCase(food)).findFirst().orElse(null);
                if (match != null) liked.add(match); else other.add(food);
            }
        }
        p.put("likedFoods", liked);
        p.put("otherLikes", String.join(", ", other));
        p.put("dislikes", prefs == null ? null : prefs.getAvoidedFoodsText());
        String diet = prefs == null ? null : prefs.getDietaryStyle();
        p.put("dietStyle", DIET_OPTIONS.stream().anyMatch(d -> d[0].equals(diet)) ? diet : "flexible");
    }
}
