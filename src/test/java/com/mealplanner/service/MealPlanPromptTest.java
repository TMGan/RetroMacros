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
    void swapPromptCarriesAllergiesAndPreferences() {
        String prompt = service.buildSwapPrompt(new MacroTargets(500, 40, 50, 15), "Salmon bowl", List.of("Dairy"), "- Foods to avoid: mushrooms");

        assertThat(prompt).contains("The client must not eat: Dairy");
        assertThat(prompt).contains("Foods to avoid: mushrooms");
        assertThat(prompt).contains("clearly different from: Salmon bowl");
    }
}
