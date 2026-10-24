package com.mealplanner.service;

import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.HealthCondition;
import com.mealplanner.model.User;
import com.mealplanner.repository.BodyCheckInRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Nudges a client's daily calories once a week from their weigh-ins, the way a coach would:
 * a little less after a 2-week stall on fat loss, a little more when weight is dropping
 * faster than 1% a week. The calorie floor in MacroCalculatorService still applies on top.
 */
@Service
public class WeeklyAdjustmentService {

    static final int STEP = 100;
    static final int MAX_CUT = -300;
    static final int MAX_RAISE = 300;
    // Pounds that count as "moved" over the 2-week stall window.
    static final double STALL_LBS = 0.5;
    static final double FAST_LOSS_SHARE = 0.01;
    // A cut needs this long to show on the scale before another one.
    static final int DAYS_BETWEEN_CUTS = 14;
    // Weigh-ins older than this don't drive a change.
    static final int LATEST_MAX_AGE_DAYS = 10;

    private final BodyCheckInRepository checkInRepository;

    public WeeklyAdjustmentService(BodyCheckInRepository checkInRepository) {
        this.checkInRepository = checkInRepository;
    }

    /**
     * Runs the check once per Monday-to-Sunday week and records the result on the user
     * (the caller saves). Returns the change in calories a day: -100, +100 or 0.
     */
    public int update(User user, String goal, LocalDate today, boolean onRamp, Set<HealthCondition> health) {
        LocalDate week = ChallengeService.weekStart(today);
        if (week.equals(user.getAdjustmentCheckedWeek())) return 0;
        user.setAdjustmentCheckedWeek(week);

        // No calorie cuts during pregnancy or for anyone with an eating disorder history. A GLP-1
        // medication already makes the deficit, so a stall is for their doctor, not a smaller plan.
        boolean noCuts = health.contains(HealthCondition.PREGNANT) || health.contains(HealthCondition.EATING_DISORDER)
                || health.contains(HealthCondition.GLP1);
        int change = decide(MacroCalculatorService.goalKey(goal), checkInRepository.findByUserOrderByCheckInDateAsc(user),
                today, user.getCalorieAdjustment(), user.getCalorieAdjustedOn(), onRamp, noCuts);
        if (change != 0) {
            user.setCalorieAdjustment(user.getCalorieAdjustment() + change);
            user.setCalorieAdjustedOn(today);
        }
        return change;
    }

    /** @param checkIns oldest first */
    static int decide(String goalKey, List<BodyCheckIn> checkIns, LocalDate today, int current,
                      LocalDate lastAdjustedOn, boolean onRamp, boolean noCuts) {
        List<BodyCheckIn> weighed = checkIns.stream()
                .filter(c -> c.getWeightLbs() != null && c.getCheckInDate() != null && !c.getCheckInDate().isAfter(today))
                .toList();
        if (weighed.isEmpty()) return 0;
        BodyCheckIn latest = weighed.get(weighed.size() - 1);
        if (latest.getCheckInDate().isBefore(today.minusDays(LATEST_MAX_AGE_DAYS))) return 0;

        // Losing faster than 1% of body weight a week: eat a bit more, whatever the goal.
        BodyCheckIn weekAgo = closestBefore(weighed, latest.getCheckInDate(), 6);
        if (weekAgo != null && current < MAX_RAISE) {
            long days = ChronoUnit.DAYS.between(weekAgo.getCheckInDate(), latest.getCheckInDate());
            double lostPerWeek = (weekAgo.getWeightLbs() - latest.getWeightLbs()) * 7 / days;
            if (lostPerWeek > weekAgo.getWeightLbs() * FAST_LOSS_SHARE) return STEP;
        }

        // Fat loss stalled for 2 weeks: eat a bit less. The ramp is already lowering calories.
        boolean cutAllowed = goalKey.equals("lose") && !onRamp && !noCuts && current > MAX_CUT
                && (lastAdjustedOn == null || ChronoUnit.DAYS.between(lastAdjustedOn, today) >= DAYS_BETWEEN_CUTS);
        if (cutAllowed) {
            BodyCheckIn twoWeeksAgo = closestBefore(weighed, latest.getCheckInDate(), 13);
            if (twoWeeksAgo != null && twoWeeksAgo.getWeightLbs() - latest.getWeightLbs() < STALL_LBS) return -STEP;
        }
        return 0;
    }

    /** The latest weigh-in at least {@code minDays} and at most 21 days before {@code date}. */
    private static BodyCheckIn closestBefore(List<BodyCheckIn> weighed, LocalDate date, int minDays) {
        LocalDate newest = date.minusDays(minDays);
        LocalDate oldest = date.minusDays(21);
        BodyCheckIn found = null;
        for (BodyCheckIn c : weighed) {
            LocalDate d = c.getCheckInDate();
            if (!d.isAfter(newest) && !d.isBefore(oldest)) found = c;
        }
        return found;
    }
}
