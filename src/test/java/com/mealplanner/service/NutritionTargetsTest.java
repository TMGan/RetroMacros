package com.mealplanner.service;

import com.mealplanner.model.HealthCondition;
import com.mealplanner.model.NutritionTargets;
import com.mealplanner.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionTargetsTest {

    private final MacroCalculatorService calc = new MacroCalculatorService();

    private UserProfile person(double weight, int feet, int inches, int age, String sex, String activity, String goal) {
        return new UserProfile(weight, feet, inches, age, sex, activity, goal, List.of());
    }

    private UserProfile man() {
        return person(200, 5, 10, 35, "Male", "Moderately Active", "Lose Weight");
    }

    private static int macroCalories(NutritionTargets t) {
        return t.getProtein() * 4 + t.getCarbs() * 4 + t.getFat() * 9;
    }

    @Test
    void fatLossIsTwentyPercentUnderMaintenance() {
        NutritionTargets t = calc.buildTargets(man(), null);

        assertThat(t.getCalories()).isCloseTo((int) Math.round(t.getMaintenanceCalories() * 0.8), org.assertj.core.data.Offset.offset(1));
        assertThat(t.isFloorApplied()).isFalse();
        // Balanced: 0.7 g protein per lb.
        assertThat(t.getProtein()).isEqualTo(140);
        assertThat(macroCalories(t)).isCloseTo(t.getCalories(), org.assertj.core.data.Offset.offset(15));
    }

    @Test
    void neverGoesBelowRestingBurnOrTheMinimum() {
        // Small, sedentary, older woman: 20% under maintenance would be under 1200.
        NutritionTargets t = calc.buildTargets(person(115, 4, 11, 70, "Female", "Sedentary", "Lose Weight"), null);

        assertThat(t.getCalories()).isGreaterThanOrEqualTo(Math.min(1200, t.getMaintenanceCalories()));
        assertThat(t.getCalories()).isGreaterThanOrEqualTo(Math.min(t.getBmr(), t.getMaintenanceCalories()));
        assertThat(t.getCalories()).isLessThanOrEqualTo(t.getMaintenanceCalories());
        assertThat(t.isFloorApplied()).isTrue();
    }

    @Test
    void buildIsTenPercentOver() {
        NutritionTargets t = calc.buildTargets(person(170, 5, 10, 28, "Male", "Very Active", "Build Muscle"), null);

        assertThat(t.getCalories()).isCloseTo((int) Math.round(t.getMaintenanceCalories() * 1.1), org.assertj.core.data.Offset.offset(1));
    }

    @Test
    void macroStyleChangesTheSplitButNotCalories() {
        UserProfile balanced = man();
        UserProfile highCarb = man();
        highCarb.setMacroStyle(UserProfile.STYLE_HIGH_CARB);
        UserProfile highProtein = man();
        highProtein.setMacroStyle(UserProfile.STYLE_HIGH_PROTEIN);

        NutritionTargets a = calc.buildTargets(balanced, null);
        NutritionTargets b = calc.buildTargets(highCarb, null);
        NutritionTargets c = calc.buildTargets(highProtein, null);

        assertThat(b.getCalories()).isEqualTo(a.getCalories());
        assertThat(c.getCalories()).isEqualTo(a.getCalories());
        assertThat(b.getProtein()).isLessThan(a.getProtein());
        assertThat(b.getCarbs()).isGreaterThan(a.getCarbs());
        assertThat(c.getProtein()).isEqualTo(200);
    }

    @Test
    void largerClientsGetProteinFromAHealthyReferenceWeight() {
        // 300 lb at 5'6" is a BMI near 48; protein follows the weight at BMI 25 (about 155 lb).
        NutritionTargets t = calc.buildTargets(person(300, 5, 6, 50, "Female", "Sedentary", "Lose Weight"), null);

        assertThat(t.getReferenceWeight()).isBetween(150.0, 160.0);
        assertThat(t.getProtein()).isBetween(100, 115);
    }

    @Test
    void goalWeightIsUsedWhenSet() {
        UserProfile p = person(300, 5, 6, 50, "Female", "Sedentary", "Lose Weight");
        p.setGoalWeight(180.0);

        assertThat(calc.buildTargets(p, null).getReferenceWeight()).isEqualTo(180.0);
    }

    @Test
    void customSplitStillRespectsTheMinimums() {
        UserProfile p = man();
        p.setMacroStyle(UserProfile.STYLE_CUSTOM);
        p.setCustomProteinPercent(20);
        p.setCustomCarbsPercent(60);
        p.setCustomFatPercent(20);

        NutritionTargets t = calc.buildTargets(p, null);

        assertThat(t.getProtein()).isGreaterThanOrEqualTo(100); // 0.5 g per lb of 200 lb
        assertThat(t.getFat() * 9).isGreaterThanOrEqualTo((int) (t.getCalories() * 0.20) - 9);
    }

    @Test
    void lowerCarbKeepsAtLeast75gCarbs() {
        UserProfile p = person(160, 5, 4, 45, "Female", "Sedentary", "Lose Weight");
        p.setMacroStyle(UserProfile.STYLE_LOW_CARB);

        assertThat(calc.buildTargets(p, null).getCarbs()).isGreaterThanOrEqualTo(75);
    }

    @Test
    void rampStepsTowardTheFullTarget() {
        NutritionTargets w1 = calc.buildTargets(man(), 1);
        NutritionTargets w2 = calc.buildTargets(man(), 2);
        NutritionTargets w4 = calc.buildTargets(man(), 4);
        NutritionTargets full = calc.buildTargets(man(), null);

        assertThat(w1.getCalories()).isCloseTo((int) Math.round(w1.getMaintenanceCalories() * 0.95), org.assertj.core.data.Offset.offset(1));
        assertThat(w1.getCalories()).isGreaterThan(w2.getCalories());
        assertThat(w2.getCalories()).isGreaterThan(w4.getCalories());
        assertThat(w4.getCalories()).isEqualTo(full.getCalories());
        assertThat(w1.getProtein()).isLessThan(w4.getProtein());
        assertThat(w1.getRampWeek()).isEqualTo(1);
    }

    @Test
    void rampWeekCountsFromTheStartDate() {
        LocalDate start = LocalDate.of(2026, 10, 26);
        assertThat(MacroCalculatorService.rampWeek(start, start)).isEqualTo(1);
        assertThat(MacroCalculatorService.rampWeek(start, start.plusDays(7))).isEqualTo(2);
        assertThat(MacroCalculatorService.rampWeek(start, start.plusDays(27))).isEqualTo(4);
        assertThat(MacroCalculatorService.rampWeek(start, start.plusDays(28))).isNull();
        assertThat(MacroCalculatorService.rampWeek(null, start)).isNull();
    }

    @Test
    void healthFlagHoldsAtMaintenanceUntilCleared() {
        UserProfile p = man();
        p.setHealthConditions(EnumSet.of(HealthCondition.DIABETES_MEDS));

        NutritionTargets held = calc.buildTargets(p, null);
        assertThat(held.isHealthHold()).isTrue();
        assertThat(held.getCalories()).isEqualTo(held.getMaintenanceCalories());
        assertThat(held.isCreatineNeedsDoctor()).isTrue();

        p.setHealthCleared(true);
        NutritionTargets cleared = calc.buildTargets(p, null);
        assertThat(cleared.isHealthHold()).isFalse();
        assertThat(cleared.getCalories()).isLessThan(cleared.getMaintenanceCalories());
    }

    @Test
    void kidneyDiseaseCapsProtein() {
        UserProfile p = man();
        p.setMacroStyle(UserProfile.STYLE_HIGH_PROTEIN);
        p.setHealthConditions(EnumSet.of(HealthCondition.KIDNEY));

        assertThat(calc.buildTargets(p, null).getProtein()).isLessThanOrEqualTo(72); // 0.36 g per lb of 200 lb
    }

    @Test
    void fiberAndWaterScaleWithTheClient() {
        NutritionTargets t = calc.buildTargets(man(), null);

        assertThat(t.getFiber()).isEqualTo(Math.round(t.getCalories() * 14 / 1000f));
        assertThat(t.getWaterOz()).isEqualTo(100);
    }
}
