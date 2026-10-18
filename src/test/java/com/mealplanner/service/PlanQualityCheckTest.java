package com.mealplanner.service;

import com.mealplanner.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanQualityCheckTest {

    private static Meal meal(String name, String type, int calories, int protein, String... foods) {
        List<FoodItem> items = new ArrayList<>();
        for (String f : foods) items.add(new FoodItem(f, "1 serving"));
        Meal m = new Meal(name, items, new MacroTargets(calories, protein, 50, 15));
        m.setType(type);
        return m;
    }

    private static MealPlan plan(Meal... meals) {
        Day day = new Day(1, List.of(meals), new MacroTargets(9999, 999, 999, 999));
        MealPlan plan = new MealPlan(List.of(day), null);
        PlanQualityCheck.recomputeTotals(plan);
        return plan;
    }

    @Test
    void totalsComeFromTheMealsNotTheModel() {
        MealPlan p = plan(meal("Oats", "breakfast", 500, 30, "oats", "spinach"), meal("Bowl", "lunch", 700, 50, "rice", "broccoli"));

        assertThat(p.getDays().get(0).getDailyTotal().getCalories()).isEqualTo(1200);
        assertThat(p.getDays().get(0).getDailyTotal().getProtein()).isEqualTo(80);
    }

    @Test
    void flagsAMainMealWithNoVegetable() {
        MealPlan p = plan(
                meal("Pancakes", "breakfast", 600, 40, "pancakes", "maple syrup"),
                meal("Salmon bowl", "dinner", 700, 50, "salmon", "rice", "roasted broccoli"),
                meal("Yogurt", "snack", 300, 20, "Greek yogurt", "blueberries"));

        List<String> problems = PlanQualityCheck.problems(p, new MacroTargets(1600, 110, 150, 50));

        assertThat(problems).anyMatch(s -> s.contains("breakfast") && s.contains("no vegetable"));
        assertThat(problems).noneMatch(s -> s.contains("dinner") && s.contains("no vegetable"));
        assertThat(problems).noneMatch(s -> s.contains("snack"));
    }

    @Test
    void seasoningAndLookalikesDontCountAsVegetables() {
        assertThat(PlanQualityCheck.hasVegetable(meal("Tuna salad", "lunch", 500, 40, "tuna salad", "garlic", "corn tortilla"))).isFalse();
        assertThat(PlanQualityCheck.hasVegetable(meal("Snack", "lunch", 300, 10, "peanut butter", "crackers"))).isFalse();
        assertThat(PlanQualityCheck.hasVegetable(meal("Wrap", "lunch", 500, 40, "chicken", "baby carrots"))).isTrue();
        assertThat(PlanQualityCheck.hasVegetable(meal("Eggs", "breakfast", 400, 30, "eggs", "cherry tomatoes"))).isTrue();
    }

    @Test
    void typeFallsBackToTheMealName() {
        Meal m = meal("Breakfast burrito", null, 500, 30, "eggs", "tortilla");
        assertThat(PlanQualityCheck.typeOf(m)).isEqualTo("breakfast");
        assertThat(PlanQualityCheck.isMainMeal(meal("Afternoon snack", null, 200, 10, "apple"))).isFalse();
    }

    @Test
    void flagsDaysThatMissTheTargets() {
        MealPlan p = plan(meal("Bowl", "lunch", 1200, 60, "rice", "broccoli"));

        List<String> problems = PlanQualityCheck.problems(p, new MacroTargets(2000, 150, 200, 60));

        assertThat(problems).anyMatch(s -> s.contains("1200 calories"));
        assertThat(problems).anyMatch(s -> s.contains("60g protein"));
    }

    @Test
    void closeEnoughPasses() {
        MealPlan p = plan(meal("A", "breakfast", 980, 72, "oats", "spinach"), meal("B", "dinner", 1000, 70, "chicken", "zucchini"));

        assertThat(PlanQualityCheck.problems(p, new MacroTargets(2000, 145, 200, 60))).isEmpty();
        assertThat(PlanQualityCheck.accuracyScore(p, new MacroTargets(2000, 145, 200, 60))).isEqualTo(99.0);
    }
}
