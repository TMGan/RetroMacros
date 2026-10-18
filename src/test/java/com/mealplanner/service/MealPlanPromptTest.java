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
        assertThat(prompt).contains("rotisserie chicken");
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
    void swapPromptCarriesAllergiesAndPreferences() {
        String prompt = service.buildSwapPrompt(new MacroTargets(500, 40, 50, 15), "Salmon bowl", List.of("Dairy"), "- Foods to avoid: mushrooms");

        assertThat(prompt).contains("The client must not eat: Dairy");
        assertThat(prompt).contains("Foods to avoid: mushrooms");
        assertThat(prompt).contains("clearly different from: Salmon bowl");
    }
}
