package com.mealplanner.service;

import com.mealplanner.model.FoodItem;
import com.mealplanner.model.Meal;
import com.mealplanner.model.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllergenGuardTest {

    private Meal meal(String name, String... items) {
        Meal meal = new Meal();
        meal.setName(name);
        meal.setFoods(java.util.Arrays.stream(items).map(i -> new FoodItem(i, "1 serving")).toList());
        return meal;
    }

    @Test
    void catchesFormAllergiesThroughIngredientNames() {
        assertThat(AllergenGuard.violations(List.of("Dairy"), meal("Breakfast", "Greek yogurt", "Berries"))).containsExactly("Dairy");
        assertThat(AllergenGuard.violations(List.of("Shellfish"), meal("Dinner", "Garlic shrimp", "Rice"))).containsExactly("Shellfish");
        assertThat(AllergenGuard.violations(List.of("Gluten/Wheat"), meal("Lunch", "Turkey wrap"))).containsExactly("Gluten/Wheat");
        assertThat(AllergenGuard.violations(List.of("Eggs"), meal("Breakfast", "Scrambled eggs"))).containsExactly("Eggs");
        assertThat(AllergenGuard.violations(List.of("Fish"), meal("Dinner", "Grilled salmon"))).containsExactly("Fish");
    }

    @Test
    void doesNotFlagLookalikes() {
        assertThat(AllergenGuard.violations(List.of("Dairy"), meal("Snack", "Peanut butter", "Almond milk", "Apple"))).isEmpty();
        assertThat(AllergenGuard.violations(List.of("Eggs"), meal("Dinner", "Roasted eggplant"))).isEmpty();
        assertThat(AllergenGuard.violations(List.of("Tree Nuts"), meal("Snack", "Coconut chips"))).isEmpty();
        assertThat(AllergenGuard.violations(List.of("Gluten/Wheat"), meal("Lunch", "Corn tortilla tacos", "Rice noodle bowl"))).isEmpty();
    }

    @Test
    void checksRecipeIngredientsAndFreeTextAllergies() {
        Meal dinner = meal("Dinner", "Chicken stir-fry");
        Recipe recipe = new Recipe();
        recipe.setName("Chicken stir-fry");
        recipe.setIngredients(List.of("6 oz chicken", "1 tbsp soy sauce", "2 roma tomatoes"));
        dinner.setRecipe(recipe);

        assertThat(AllergenGuard.violations(List.of("Soy", "tomatoes", "Peanuts"), dinner))
                .containsExactlyInAnyOrder("Soy", "tomatoes");
    }

    @Test
    void catchesAllergensHiddenInSauces() {
        assertThat(AllergenGuard.violations(List.of("Gluten/Wheat", "Soy"), meal("Dinner", "Chicken", "1 tbsp soy sauce")))
                .containsExactlyInAnyOrder("Gluten/Wheat", "Soy");
        assertThat(AllergenGuard.violations(List.of("Tree Nuts", "Dairy"), meal("Lunch", "Pasta", "2 tbsp basil pesto")))
                .containsExactlyInAnyOrder("Tree Nuts", "Dairy");
        assertThat(AllergenGuard.violations(List.of("Dairy"), meal("Lunch", "Salad", "2 tbsp light ranch"))).containsExactly("Dairy");
        assertThat(AllergenGuard.violations(List.of("Fish"), meal("Dinner", "Steak", "1 tsp Worcestershire sauce"))).containsExactly("Fish");
        assertThat(AllergenGuard.violations(List.of("Gluten/Wheat"), meal("Dinner", "Rice bowl", "1 tbsp gluten-free soy sauce"))).isEmpty();
    }

    @Test
    void noAllergiesMeansNoViolations() {
        assertThat(AllergenGuard.violations(List.of(), meal("Dinner", "Shrimp pasta with cheese"))).isEmpty();
        assertThat(AllergenGuard.violations(null, meal("Dinner", "Shrimp pasta with cheese"))).isEmpty();
    }
}
