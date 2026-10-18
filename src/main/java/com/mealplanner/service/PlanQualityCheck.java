package com.mealplanner.service;

import com.mealplanner.model.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Health rules checked in code rather than trusted to the AI: every main meal has a
 * vegetable, and each day's meals really add up to the targets. Day totals are
 * always recomputed from the meals, never taken from what the model claims.
 */
public final class PlanQualityCheck {

    // Calories may be off by this share of the target (or 100 kcal, whichever is more).
    static final double CALORIE_TOLERANCE = 0.08;
    // Protein may come in this far under the target.
    static final double PROTEIN_SHORTFALL = 0.12;

    // Word starts, so "carrot" matches "carrots" and "pea" does not match "peanut" ("peas" is listed instead).
    private static final List<String> VEGETABLES = List.of(
            // Not plain "salad": tuna, chicken, egg and pasta salad aren't vegetables.
            "spinach", "kale", "lettuce", "romaine", "arugula", "greens", "side salad", "green salad", "garden salad", "salad kit",
            "cabbage", "bok choy", "chard", "collard",
            "broccoli", "broccolini", "cauliflower", "brussels", "asparagus", "green bean", "string bean", "snap pea", "snow pea", "peas",
            "edamame", "zucchini", "squash", "eggplant", "cucumber", "celery", "carrot", "beet", "radish", "turnip", "parsnip",
            "tomato", "bell pepper", "peppers", "red pepper", "green pepper", "jalapeno", "jalapeño", "poblano", "onion", "scallion",
            "shallot", "leek", "garlic", "mushroom", "okra", "artichoke", "fennel", "sprouts", "pico de gallo", "salsa",
            // Not "corn": corn tortillas and chips would pass a meal with no vegetable in it.
            "vegetable", "veggie", "coleslaw", "slaw", "pumpkin", "sauerkraut", "kimchi");
    // Garlic and onion as seasoning don't count as the meal's vegetable on their own.
    private static final Set<String> SEASONING_ONLY = Set.of("garlic", "onion", "shallot", "scallion");

    private PlanQualityCheck() {}

    /** Replaces each day's total with the sum of its meals. */
    public static void recomputeTotals(MealPlan plan) {
        if (plan == null || plan.getDays() == null) return;
        for (Day day : plan.getDays()) day.setDailyTotal(sum(day.getMeals()));
    }

    /** Everything that breaks the rules, written so it can be fed back to the AI. Empty means it passed. */
    public static List<String> problems(MealPlan plan, MacroTargets targets) {
        List<String> out = new ArrayList<>();
        if (plan == null || plan.getDays() == null || plan.getDays().isEmpty()) {
            out.add("The plan has no days.");
            return out;
        }
        for (Day day : plan.getDays()) {
            if (day.getMeals() == null) continue;
            for (Meal meal : day.getMeals()) {
                if (isMainMeal(meal) && !hasVegetable(meal)) {
                    out.add("Day " + day.getDayNumber() + " " + label(meal) + " (" + meal.getName() + ") has no vegetable.");
                }
            }
            MacroTargets total = day.getDailyTotal() != null ? day.getDailyTotal() : sum(day.getMeals());
            if (targets != null) {
                int calorieSlack = (int) Math.max(100, targets.getCalories() * CALORIE_TOLERANCE);
                if (Math.abs(total.getCalories() - targets.getCalories()) > calorieSlack) {
                    out.add("Day " + day.getDayNumber() + " adds up to " + total.getCalories() + " calories; the target is "
                            + targets.getCalories() + ".");
                }
                if (total.getProtein() < targets.getProtein() * (1 - PROTEIN_SHORTFALL)) {
                    out.add("Day " + day.getDayNumber() + " has " + total.getProtein() + "g protein; the target is "
                            + targets.getProtein() + "g.");
                }
            }
        }
        return out;
    }

    /** 0 to 100: how close the days' calories land to the target on average. Null if it can't be measured. */
    public static Double accuracyScore(MealPlan plan, MacroTargets targets) {
        if (plan == null || plan.getDays() == null || plan.getDays().isEmpty() || targets == null || targets.getCalories() <= 0) {
            return null;
        }
        double offBy = 0;
        for (Day day : plan.getDays()) {
            MacroTargets total = day.getDailyTotal() != null ? day.getDailyTotal() : sum(day.getMeals());
            offBy += Math.abs(total.getCalories() - targets.getCalories()) / (double) targets.getCalories();
        }
        double score = 100 * (1 - offBy / plan.getDays().size());
        return Math.round(Math.max(0, score) * 10) / 10.0;
    }

    /** The average day in the plan, or null for an empty plan. */
    public static MacroTargets averageDailyTotals(MealPlan plan) {
        if (plan == null || plan.getDays() == null || plan.getDays().isEmpty()) return null;
        int n = plan.getDays().size();
        int cal = 0, p = 0, c = 0, f = 0;
        for (Day day : plan.getDays()) {
            MacroTargets t = day.getDailyTotal() != null ? day.getDailyTotal() : sum(day.getMeals());
            cal += t.getCalories();
            p += t.getProtein();
            c += t.getCarbs();
            f += t.getFat();
        }
        return new MacroTargets(Math.round(cal / (float) n), Math.round(p / (float) n), Math.round(c / (float) n), Math.round(f / (float) n));
    }

    static boolean isMainMeal(Meal meal) {
        return !"snack".equals(typeOf(meal));
    }

    /** breakfast, lunch, dinner or snack: from the meal's type, else guessed from its name. */
    static String typeOf(Meal meal) {
        String type = meal.getType() == null ? "" : meal.getType().trim().toLowerCase(Locale.ROOT);
        if (List.of("breakfast", "lunch", "dinner", "snack").contains(type)) return type;
        String name = meal.getName() == null ? "" : meal.getName().toLowerCase(Locale.ROOT);
        for (String t : List.of("breakfast", "lunch", "dinner", "snack")) {
            if (name.contains(t)) return t;
        }
        return "meal";
    }

    static boolean hasVegetable(Meal meal) {
        StringBuilder sb = new StringBuilder();
        if (meal.getFoods() != null) {
            for (FoodItem food : meal.getFoods()) sb.append(food.getItem()).append(" | ");
        }
        String foods = sb.toString().toLowerCase(Locale.ROOT);
        for (String veg : VEGETABLES) {
            if (SEASONING_ONLY.contains(veg)) continue;
            if (Pattern.compile("\\b" + Pattern.quote(veg)).matcher(foods).find()) return true;
        }
        return false;
    }

    private static String label(Meal meal) {
        String type = typeOf(meal);
        return type.equals("meal") ? "meal" : type;
    }

    private static MacroTargets sum(List<Meal> meals) {
        int cal = 0, p = 0, c = 0, f = 0, fiber = 0;
        boolean hasFiber = false;
        if (meals != null) {
            for (Meal m : meals) {
                if (m.getMacros() == null) continue;
                cal += m.getMacros().getCalories();
                p += m.getMacros().getProtein();
                c += m.getMacros().getCarbs();
                f += m.getMacros().getFat();
                if (m.getMacros().getFiber() != null) {
                    fiber += m.getMacros().getFiber();
                    hasFiber = true;
                }
            }
        }
        MacroTargets t = new MacroTargets(cal, p, c, f);
        if (hasFiber) t.setFiber(fiber);
        return t;
    }
}
