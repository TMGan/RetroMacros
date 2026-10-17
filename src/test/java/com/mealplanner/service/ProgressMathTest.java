package com.mealplanner.service;

import com.mealplanner.model.BodyCheckIn;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ProgressMathTest {

    private final LocalDate today = LocalDate.of(2026, 11, 1);

    private BodyCheckIn entry(int daysAgo, double weight) {
        BodyCheckIn e = new BodyCheckIn();
        e.setCheckInDate(today.minusDays(daysAgo));
        e.setWeightLbs(weight);
        return e;
    }

    @Test
    void weeklyRateIsTheBestFitSlopeNotTwoSingleWeighIns() {
        // Steady 1 lb/week loss with a noisy last weigh-in.
        List<BodyCheckIn> entries = new ArrayList<>();
        for (int d = 27; d >= 0; d--) entries.add(entry(d, 180 + d / 7.0));
        entries.set(entries.size() - 1, entry(0, 182.5));  // water spike on the last day

        Double rate = ProgressService.weeklyRate(entries, today);

        assertThat(rate).isCloseTo(-0.9, within(0.2));
    }

    @Test
    void weeklyRateNeedsAtLeastAWeekOfData() {
        assertThat(ProgressService.weeklyRate(List.of(entry(0, 180)), today)).isNull();
        assertThat(ProgressService.weeklyRate(List.of(entry(3, 181), entry(0, 180)), today)).isNull();
        assertThat(ProgressService.weeklyRate(List.of(entry(10, 182), entry(0, 180)), today)).isCloseTo(-1.4, within(0.01));
    }

    @Test
    void averagesOnlyTheRequestedWindow() {
        List<BodyCheckIn> entries = List.of(entry(10, 190), entry(5, 182), entry(0, 180));
        assertThat(ProgressService.averageWeight(entries, today.minusDays(6), today)).isEqualTo(181.0);
    }

    @Test
    void healthyRateDependsOnGoalAndBodyWeight() {
        assertThat(ProgressService.healthyBand("lose", 200)).containsExactly(-2.0, -1.0);
        assertThat(ProgressService.healthyBand("build", 200)).containsExactly(0.5, 1.0);
        assertThat(ProgressService.healthyBand("maintain", 200)).containsExactly(-0.5, 0.5);
    }

    @Test
    void rateStatusReadsTheRightWayForEachGoal() {
        double[] lose = ProgressService.healthyBand("lose", 200);
        assertThat(ProgressService.rateStatus("lose", -1.5, lose)).isEqualTo("on-track");
        assertThat(ProgressService.rateStatus("lose", -3.0, lose)).isEqualTo("too-fast");
        assertThat(ProgressService.rateStatus("lose", 0.0, lose)).isEqualTo("too-slow");
        assertThat(ProgressService.rateStatus("build", 2.0, ProgressService.healthyBand("build", 200))).isEqualTo("too-fast");
        assertThat(ProgressService.rateStatus("maintain", 1.0, ProgressService.healthyBand("maintain", 200))).isEqualTo("drifting-up");
        assertThat(ProgressService.rateStatus("lose", null, lose)).isEqualTo("needs-data");
    }
}
