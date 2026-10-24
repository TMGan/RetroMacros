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
