package com.mealplanner.service;

import com.mealplanner.model.NutritionTargets;
import com.mealplanner.model.UserProfile;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityMultiplierTest {

    private static final Offset<Double> CLOSE = Offset.offset(0.0001);

    private static double multiplier(String movement, int days, String intensity) {
        return MacroCalculatorService.activityMultiplier(movement, days, intensity);
    }

    @Test
    void theNormalDaySetsTheStartingPoint() {
        assertThat(multiplier("sitting", 0, null)).isCloseTo(1.2, CLOSE);
        assertThat(multiplier("walking", 0, null)).isCloseTo(1.3, CLOSE);
        assertThat(multiplier("feet", 0, null)).isCloseTo(1.4, CLOSE);
        assertThat(multiplier("physical", 0, null)).isCloseTo(1.5, CLOSE);
    }

    @Test
    void goingToTheGymCountsByHowHardItIs() {
        assertThat(multiplier("sitting", 5, "light")).isCloseTo(1.275, CLOSE);
        assertThat(multiplier("sitting", 5, "moderate")).isCloseTo(1.325, CLOSE);
        assertThat(multiplier("sitting", 5, "hard")).isCloseTo(1.4, CLOSE);
    }

    @Test
    void itNeverGoesPastTheCap() {
        assertThat(multiplier("physical", 7, "hard")).isCloseTo(1.7, CLOSE);
    }

    @Test
    void aFiveDayLightGymClientNoLongerGetsVeryActiveCalories() {
        UserProfile p = new UserProfile(159, 5, 1, 50, "Female", "described below", "Lose Weight", List.of());
        p.setDailyMovement("sitting");
        p.setWorkoutDays(5);
        p.setWorkoutIntensity("light");

        NutritionTargets t = new MacroCalculatorService().buildTargets(p, null);

        assertThat(t.getMaintenanceCalories()).isBetween(1620, 1640);
        assertThat(t.getCalories()).isBetween(1295, 1315);
    }

    @Test
    void theAnswersReadPlainly() {
        assertThat(MacroCalculatorService.describeActivity("sitting", 5, "light")).isEqualTo("Mostly sitting, 5 workouts a week (light)");
        assertThat(MacroCalculatorService.describeActivity("feet", 1, "hard")).isEqualTo("On feet most of the day, 1 workout a week (hard)");
        assertThat(MacroCalculatorService.describeActivity("walking", 0, null)).isEqualTo("Some walking and moving around, no workouts");
    }
}
