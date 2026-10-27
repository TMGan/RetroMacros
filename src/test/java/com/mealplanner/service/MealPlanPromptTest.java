package com.mealplanner.service;

import com.mealplanner.model.MacroTargets;
import com.mealplanner.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MealPlanPromptTest {

    private final MealPlanService service = new MealPlanService(null, false, true);
    private final MacroTargets targets = new MacroTargets(2200, 180, 220, 70);

    private UserProfile profile(List<String> allergies) {
        return new UserProfile(180, 5, 10, 30, "male", "Moderately Active", "Build Muscle", allergies);
    }

    @Test
    void noLongerForcesAProteinPerDay() {
        String prompt = service.buildPlanPrompt(profile(List.of("Fish", "Shellfish")), targets, null);

        assertThat(prompt).doesNotContain("Day 2: Fish");
        assertThat(prompt).doesNotContain("Chicken or Turkey");
        assertThat(prompt).contains("The client must not eat: Fish, Shellfish");
    }

    @Test
    void putsClientPreferencesInThePrompt() {
        String prompt = service.buildPlanPrompt(profile(List.of()), targets, "CLIENT'S FOOD PREFERENCES:\n- Foods to avoid: mushrooms\n");

        assertThat(prompt).contains("Foods to avoid: mushrooms");
        assertThat(prompt).contains("2200 calories");
    }

    @Test
    void formatExampleIsCleanJson() {
        String prompt = service.buildPlanPrompt(profile(List.of()), targets, null);

        assertThat(prompt).doesNotContain("\\\"");
        assertThat(prompt).contains("{\"days\": [{\"day\": number, \"meals\": [{\"name\": string");
    }

    @Test
    void promptCarriesTheHealthRules() {
        MacroTargets withFiber = new MacroTargets(2200, 180, 220, 70);
        withFiber.setFiber(31);
        String prompt = service.buildPlanPrompt(profile(List.of()), withFiber, null);

        assertThat(prompt).contains("Breakfast, lunch and dinner each include at least one vegetable");
        assertThat(prompt).contains("at least 31g fiber");
        assertThat(prompt).contains("Omega-3s");
        assertThat(prompt).contains("Never suggest medications, weight-loss drugs, peptides");
        assertThat(prompt).contains("up to 2 a day");
    }

    @Test
    void dairyAllergyGetsAPlantProteinShake() {
        String prompt = service.buildPlanPrompt(profile(List.of("Dairy")), targets, null);

        assertThat(prompt).contains("use a plant protein such as pea protein, not whey");
    }

    @Test
    void simpleModeRepeatsMealsAndAllowsConvenienceFoods() {
        UserProfile p = profile(List.of());
        p.setEffortLevel(UserProfile.EFFORT_SIMPLE);
        String prompt = service.buildPlanPrompt(p, targets, null);

        assertThat(prompt).contains("KEEP IT SIMPLE");
        assertThat(prompt).contains("same breakfast on all 3 days");
        assertThat(prompt).contains("Cook once, mix and match");
        assertThat(prompt).contains("2 different lunches and 2 different dinners");
        assertThat(prompt).contains("rotisserie chicken");
    }

    @Test
    void everyPlanGetsTheSauceRules() {
        String prompt = service.buildPlanPrompt(profile(List.of()), targets, null);

        assertThat(prompt).contains("FLAVOR (SAUCES AND SEASONINGS)");
        assertThat(prompt).contains("Use freely (few calories): salsa");
        assertThat(prompt).contains("Measure and count (more calories): olive oil");
        assertThat(prompt).contains("so the day's totals include it");
    }

    @Test
    void healthScreenAnswersReachThePrompt() {
        UserProfile p = profile(List.of());
        p.setHealthConditions(java.util.EnumSet.of(com.mealplanner.model.HealthCondition.PREGNANT,
                com.mealplanner.model.HealthCondition.EATING_DISORDER));
        p.setHealthNotes("high blood pressure");
        String prompt = service.buildPlanPrompt(p, targets, null);

        assertThat(prompt).contains("No high-mercury fish");
        assertThat(prompt).contains("never call foods clean, bad, cheat");
        assertThat(prompt).contains("high blood pressure");
    }

    @Test
    void glp1PlansAreSmallProteinFirstAndGentle() {
        UserProfile p = profile(List.of());
        p.setHealthConditions(java.util.EnumSet.of(com.mealplanner.model.HealthCondition.GLP1));
        String prompt = service.buildPlanPrompt(p, targets, null);

        assertThat(prompt).contains("Takes a GLP-1 medication");
        assertThat(prompt).contains("Protein first");
        assertThat(prompt).contains("no fried, greasy or very rich meals");
        assertThat(prompt).contains("breakfast, lunch, dinner and 2 or 3 snacks");
        assertThat(service.buildPlanPrompt(profile(List.of()), targets, null)).contains("breakfast, lunch, dinner and 1 or 2 snacks");
    }

    @Test
    void noHealthSectionWhenNothingWasChecked() {
        assertThat(service.buildPlanPrompt(profile(List.of()), targets, null)).doesNotContain("HEALTH (from");
    }

    @Test
    void samplePlanPassesTheVegetableRule() {
        MealPlanService offline = new MealPlanService(null, true, false);
        com.mealplanner.model.MealPlan plan = offline.generateSampleMealPlan(profile(List.of()), targets);

        assertThat(PlanQualityCheck.problems(plan, targets)).isEmpty();
    }

    @Test
    void promptAsksForAPersonalEverydayPlan() {
        String prompt = service.buildPlanPrompt(profile(List.of()), targets, null);
        assertThat(prompt).contains("This plan is for one specific person");
        assertThat(prompt).contains("ground beef and rice");
        assertThat(prompt).contains("ground beef, not bison");
    }

    @Test
    void recipesTakeTheirIngredientsFromTheFoodsList() {
        String prompt = service.buildPlanPrompt(profile(List.of()), targets, null);
        assertThat(prompt).doesNotContain("\"ingredients\"");
        assertThat(prompt).contains("the foods list is the ingredient list");

        com.mealplanner.model.Meal meal = new com.mealplanner.model.Meal("Bowl",
                List.of(new com.mealplanner.model.FoodItem("Chicken breast", "6 oz"), new com.mealplanner.model.FoodItem("Broccoli", "1 cup")),
                new MacroTargets(500, 40, 50, 15));
        com.mealplanner.model.Recipe recipe = new com.mealplanner.model.Recipe();
        recipe.setName("Bowl");
        meal.setRecipe(recipe);
        MealPlanService.fillRecipeIngredients(meal);
        assertThat(meal.getRecipe().getIngredients()).containsExactly("6 oz Chicken breast", "1 cup Broccoli");
    }

    @Test
    void swapPromptCarriesAllergiesAndPreferences() {
        String prompt = service.buildSwapPrompt(new MacroTargets(500, 40, 50, 15), "Salmon bowl", List.of("Dairy"), "- Foods to avoid: mushrooms");

        assertThat(prompt).contains("The client must not eat: Dairy");
        assertThat(prompt).contains("Foods to avoid: mushrooms");
        assertThat(prompt).contains("clearly different from: Salmon bowl");
    }
    @Test
    void avoidedFoodsAreAHardRuleAndOverrideExamples() {
        UserProfile p = profile(List.of());
        p.setAvoidedFoods(List.of("salmon"));
        String prompt = service.buildPlanPrompt(p, targets, null);

        assertThat(prompt).contains("The client doesn't eat: salmon. Leave these out of every meal");
        assertThat(prompt).contains("If the client avoids one fish, use another they eat");
        assertThat(service.buildPlanPrompt(profile(List.of()), targets, null)).doesNotContain("The client doesn't eat");
    }

    @Test
    void aPlanWithAnAvoidedFoodIsRedoneWithoutIt() throws Exception {
        AiClient ai = org.mockito.Mockito.mock(AiClient.class);
        MealPlanService live = new MealPlanService(ai, false, false);
        UserProfile p = profile(List.of());
        p.setAvoidedFoods(List.of("salmon"));
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        String withSalmon = json.writeValueAsString(new MealPlanService(null, true, false).generateSampleMealPlan(p, targets));
        assertThat(withSalmon.toLowerCase()).contains("salmon");
        String withoutSalmon = withSalmon.replaceAll("(?i)salmon", "chicken");
        org.mockito.Mockito.when(ai.complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(AiClient.Kind.PLAN), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(withSalmon, withoutSalmon);

        com.mealplanner.model.MealPlan plan = live.generateMealPlan(null, p, targets, null);

        assertThat(AllergenGuard.violations(List.of("salmon"), plan)).isEmpty();
        org.mockito.ArgumentCaptor<String> prompts = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(ai, org.mockito.Mockito.times(2)).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(AiClient.Kind.PLAN), prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("which this client doesn't eat");
    }
}
