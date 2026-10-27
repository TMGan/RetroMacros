package com.mealplanner.service;

import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.NutritionTargets;
import com.mealplanner.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyAdjustmentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    /** Weigh-ins as (days ago, pounds) pairs, oldest first. */
    private static List<BodyCheckIn> weighIns(double... daysAgoAndLbs) {
        List<BodyCheckIn> list = new ArrayList<>();
        for (int i = 0; i < daysAgoAndLbs.length; i += 2) {
            BodyCheckIn c = new BodyCheckIn();
            c.setCheckInDate(TODAY.minusDays((long) daysAgoAndLbs[i]));
            c.setWeightLbs(daysAgoAndLbs[i + 1]);
            list.add(c);
        }
        return list;
    }

    private static int decide(String goal, List<BodyCheckIn> checkIns) {
        return WeeklyAdjustmentService.decide(goal, checkIns, TODAY, 0, null, false, false);
    }

    @Test
    void twoWeekStallOnFatLossTakesOff100() {
        assertThat(decide("lose", weighIns(14, 200, 7, 199.8, 0, 199.9))).isEqualTo(-100);
    }

    @Test
    void steadyLossChangesNothing() {
        assertThat(decide("lose", weighIns(14, 202, 7, 201, 0, 200))).isZero();
    }

    @Test
    void losingOverOnePercentAWeekAdds100WhateverTheGoal() {
        // 200 to 197 in a week is 1.5%.
        assertThat(decide("lose", weighIns(7, 200, 0, 197))).isEqualTo(100);
        assertThat(decide("maintain", weighIns(7, 200, 0, 197))).isEqualTo(100);
    }

    @Test
    void stallOnlyCountsForFatLoss() {
        assertThat(decide("maintain", weighIns(14, 200, 0, 200))).isZero();
        assertThat(decide("build", weighIns(14, 200, 0, 200))).isZero();
    }

    @Test
    void needsRecentAndOldEnoughWeighIns() {
        assertThat(decide("lose", weighIns(0, 200))).isZero();
        assertThat(decide("lose", weighIns(7, 200, 0, 200))).isZero();          // only one week of data
        assertThat(decide("lose", weighIns(30, 200, 15, 200))).isZero();        // latest is too old
        assertThat(decide("lose", List.of())).isZero();
    }

    @Test
    void cutsWaitTwoWeeksStopAtTheCapAndSkipRampAndHealthHolds() {
        List<BodyCheckIn> stalled = weighIns(14, 200, 0, 200);
        assertThat(WeeklyAdjustmentService.decide("lose", stalled, TODAY, -100, TODAY.minusDays(7), false, false)).isZero();
        assertThat(WeeklyAdjustmentService.decide("lose", stalled, TODAY, -100, TODAY.minusDays(14), false, false)).isEqualTo(-100);
        assertThat(WeeklyAdjustmentService.decide("lose", stalled, TODAY, -300, null, false, false)).isZero();
        assertThat(WeeklyAdjustmentService.decide("lose", stalled, TODAY, 0, null, true, false)).isZero();
        assertThat(WeeklyAdjustmentService.decide("lose", stalled, TODAY, 0, null, false, true)).isZero();
    }

    @Test
    void glp1KeepsNormalTargetsButNeverGetsAStallCut() {
        MacroCalculatorService calc = new MacroCalculatorService();
        UserProfile p = new UserProfile(200, 5, 10, 40, "male", "Moderately Active", "Lose Weight", List.of());
        int normal = calc.buildTargets(p, null).getCalories();
        p.setHealthConditions(java.util.EnumSet.of(com.mealplanner.model.HealthCondition.GLP1));
        NutritionTargets glp1 = calc.buildTargets(p, null);
        assertThat(glp1.isHealthHold()).isFalse();
        assertThat(glp1.getCalories()).isEqualTo(normal);

        com.mealplanner.repository.BodyCheckInRepository repo = org.mockito.Mockito.mock(com.mealplanner.repository.BodyCheckInRepository.class);
        org.mockito.Mockito.when(repo.findByUserOrderByCheckInDateAsc(org.mockito.ArgumentMatchers.any())).thenReturn(weighIns(14, 200, 0, 200));
        com.mealplanner.model.User user = new com.mealplanner.model.User("g@example.com", "G", "g");
        int change = new WeeklyAdjustmentService(repo).update(user, "Lose Weight", TODAY, false,
                java.util.EnumSet.of(com.mealplanner.model.HealthCondition.GLP1));
        assertThat(change).isZero();
        assertThat(new WeeklyAdjustmentService(repo).update(new com.mealplanner.model.User("n@example.com", "N", "n"), "Lose Weight", TODAY, false,
                java.util.EnumSet.noneOf(com.mealplanner.model.HealthCondition.class))).isEqualTo(-100);
    }

    @Test
    void theCoachsCaloriesWinOverTheFormulaHoldAndAdjustment() {
        MacroCalculatorService calc = new MacroCalculatorService();
        UserProfile p = new UserProfile(200, 5, 10, 65, "male", "Moderately Active", "Lose Weight", List.of());
        p.setHealthConditions(java.util.EnumSet.of(com.mealplanner.model.HealthCondition.DIABETES_MEDS));
        p.setCalorieAdjustment(-100);
        p.setCalorieOverride(1900);

        NutritionTargets t = calc.buildTargets(p, null);

        assertThat(t.getCalories()).isEqualTo(1900);
        assertThat(t.isCoachSet()).isTrue();
        assertThat(t.isHealthHold()).isFalse();
        // Macros are still split from the coach's number.
        assertThat(t.getProtein() * 4 + t.getCarbs() * 4 + t.getFat() * 9).isBetween(1880, 1920);
    }

    @Test
    void adjustmentMovesTheTargetButNeverBelowTheFloor() {
        MacroCalculatorService calc = new MacroCalculatorService();
        UserProfile p = new UserProfile(200, 5, 10, 40, "male", "Moderately Active", "Lose Weight", List.of());
        int base = calc.buildTargets(p, null).getCalories();
        p.setCalorieAdjustment(-100);
        assertThat(calc.buildTargets(p, null).getCalories()).isEqualTo(base - 100);

        // A small, inactive client already sits on the floor; a cut can't take them under it.
        UserProfile small = new UserProfile(120, 5, 0, 70, "female", "Sedentary", "Lose Weight", List.of());
        int floored = calc.buildTargets(small, null).getCalories();
        small.setCalorieAdjustment(-300);
        NutritionTargets cut = calc.buildTargets(small, null);
        assertThat(cut.getCalories()).isEqualTo(floored);
        assertThat(cut.isFloorApplied()).isTrue();

        // A leftover cut doesn't follow a client who switches to maintenance.
        UserProfile maintain = new UserProfile(200, 5, 10, 40, "male", "Moderately Active", "Maintain Weight", List.of());
        int maintenance = calc.buildTargets(maintain, null).getCalories();
        maintain.setCalorieAdjustment(-200);
        assertThat(calc.buildTargets(maintain, null).getCalories()).isEqualTo(maintenance);
    }
}
