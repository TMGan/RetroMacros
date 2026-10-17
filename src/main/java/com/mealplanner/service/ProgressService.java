package com.mealplanner.service;

import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.FoodLog;
import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.repository.BodyCheckInRepository;
import com.mealplanner.repository.FoodLogRepository;
import com.mealplanner.repository.SavedMealPlanRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/**
 * Turns a client's check-ins and food log into the numbers on the progress page.
 *
 * Daily weights swing with water and food, so "current" and "start" are 7-day
 * averages and the weekly rate is a best-fit line over the last 4 weeks, not a
 * comparison of two single weigh-ins.
 */
@Service
public class ProgressService {

    static final int WINDOW_DAYS = 7;
    static final int RATE_WINDOW_DAYS = 28;

    private final BodyCheckInRepository checkInRepository;
    private final FoodLogRepository foodLogRepository;
    private final SavedMealPlanRepository savedMealPlanRepository;
    private final FoodLogService foodLogService;

    public ProgressService(BodyCheckInRepository checkInRepository,
                           FoodLogRepository foodLogRepository,
                           SavedMealPlanRepository savedMealPlanRepository,
                           FoodLogService foodLogService) {
        this.checkInRepository = checkInRepository;
        this.foodLogRepository = foodLogRepository;
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.foodLogService = foodLogService;
    }

    /** Saves a check-in for the given day, replacing that day's earlier one if there is one. */
    public BodyCheckIn saveCheckIn(User user, LocalDate date, BodyCheckIn values) {
        BodyCheckIn entry = checkInRepository.findByUserAndCheckInDate(user, date).orElseGet(BodyCheckIn::new);
        entry.setUser(user);
        entry.setCheckInDate(date);
        entry.setWeightLbs(values.getWeightLbs());
        entry.setBodyFatPct(values.getBodyFatPct());
        entry.setEnergy(values.getEnergy());
        entry.setSleep(values.getSleep());
        entry.setHunger(values.getHunger());
        entry.setStress(values.getStress());
        entry.setNote(values.getNote());
        return checkInRepository.save(entry);
    }

    public Optional<BodyCheckIn> checkInOn(User user, LocalDate date) {
        return checkInRepository.findByUserAndCheckInDate(user, date);
    }

    /** True when the client has no check-in in the last 7 days (including today). */
    public boolean missedCheckIn(User user) {
        LocalDate last = lastCheckInDate(user);
        return last == null || last.isBefore(foodLogService.today(user).minusDays(WINDOW_DAYS - 1));
    }

    /** Newest first, for the coach's view of recent notes and scores. */
    public List<BodyCheckIn> recentCheckIns(User user, int limit) {
        List<BodyCheckIn> all = new ArrayList<>(checkInRepository.findByUserOrderByCheckInDateAsc(user));
        Collections.reverse(all);
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    public LocalDate lastCheckInDate(User user) {
        return checkInRepository.findTopByUserOrderByCheckInDateDesc(user).map(BodyCheckIn::getCheckInDate).orElse(null);
    }

    /** Everything the progress page shows, for the client or for the coach. */
    public Map<String, Object> summary(User user) {
        List<BodyCheckIn> entries = checkInRepository.findByUserOrderByCheckInDateAsc(user);
        LocalDate today = foodLogService.today(user);
        Map<String, Object> s = new LinkedHashMap<>();
        // Templates read these keys directly, so every one exists even when there is no data yet.
        for (String key : List.of("firstDate", "latestDate", "latestEntry", "weeksIn", "startWeight", "currentWeight",
                "change", "weeklyRate", "toGoal", "toGoalAbs", "projectedDate")) {
            s.put(key, null);
        }
        s.put("composition", Map.of("hasData", false));
        s.put("feelings", List.of());
        s.put("showRecap", false);
        s.put("hasData", !entries.isEmpty());
        s.put("entryCount", entries.size());
        s.put("missedCheckIn", entries.isEmpty() || entries.get(entries.size() - 1).getCheckInDate().isBefore(today.minusDays(WINDOW_DAYS - 1)));
        s.put("goalWeight", user.getGoalWeightLbs());
        s.put("nutrition", nutrition(user, today, entries.isEmpty() ? null : entries.get(0).getCheckInDate()));
        String goal = goalType(user, entries);
        s.put("goalType", goal);
        if (entries.isEmpty()) return s;

        BodyCheckIn first = entries.get(0);
        BodyCheckIn latest = entries.get(entries.size() - 1);
        s.put("firstDate", first.getCheckInDate());
        s.put("latestDate", latest.getCheckInDate());
        s.put("latestEntry", latest);
        s.put("weeksIn", ChronoUnit.DAYS.between(first.getCheckInDate(), today) / WINDOW_DAYS + 1);

        double start = averageWeight(entries, first.getCheckInDate(), first.getCheckInDate().plusDays(WINDOW_DAYS - 1));
        double current = averageWeight(entries, latest.getCheckInDate().minusDays(WINDOW_DAYS - 1), latest.getCheckInDate());
        s.put("startWeight", round1(start));
        s.put("currentWeight", round1(current));
        s.put("change", round1(current - start));

        Double weeklyRate = weeklyRate(entries, latest.getCheckInDate());
        s.put("weeklyRate", weeklyRate == null ? null : round1(weeklyRate));

        Double goalWeight = user.getGoalWeightLbs();
        if (goalWeight != null) {
            double toGoal = goalWeight - current;
            s.put("toGoal", round1(toGoal));
            s.put("toGoalAbs", round1(Math.abs(toGoal)));
            if (weeklyRate != null && Math.abs(weeklyRate) >= 0.05 && Math.signum(weeklyRate) == Math.signum(toGoal)) {
                long weeks = Math.round(Math.ceil(toGoal / weeklyRate));
                if (weeks <= 104) s.put("projectedDate", today.plusWeeks(weeks));
            }
        }

        s.put("composition", composition(entries));
        s.put("feelings", feelings(entries));
        s.put("showRecap", entries.size() >= 2);
        return s;
    }

    /** Series for the charts: dates plus numbers only, so it is safe to embed in the page as JSON. */
    public Map<String, Object> chartData(User user) {
        List<BodyCheckIn> entries = checkInRepository.findByUserOrderByCheckInDateAsc(user);
        List<String> dates = new ArrayList<>();
        List<Double> weight = new ArrayList<>();
        List<Double> trend = new ArrayList<>();
        List<Double> bodyFat = new ArrayList<>();
        for (BodyCheckIn e : entries) {
            dates.add(e.getCheckInDate().toString());
            weight.add(e.getWeightLbs());
            trend.add(round1(averageWeight(entries, e.getCheckInDate().minusDays(WINDOW_DAYS - 1), e.getCheckInDate())));
            bodyFat.add(e.getBodyFatPct());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dates", dates);
        data.put("weight", weight);
        data.put("trend", trend);
        data.put("bodyFat", bodyFat);
        data.put("goalWeight", user.getGoalWeightLbs());
        return data;
    }

    // --- calculations ---

    static double averageWeight(List<BodyCheckIn> entries, LocalDate from, LocalDate to) {
        return entries.stream()
                .filter(e -> !e.getCheckInDate().isBefore(from) && !e.getCheckInDate().isAfter(to))
                .mapToDouble(BodyCheckIn::getWeightLbs)
                .average().orElse(Double.NaN);
    }

    /** Pounds per week from a best-fit line over the last 4 weeks. Null until there are two weeks of data. */
    static Double weeklyRate(List<BodyCheckIn> entries, LocalDate latest) {
        LocalDate from = latest.minusDays(RATE_WINDOW_DAYS - 1);
        List<BodyCheckIn> window = entries.stream().filter(e -> !e.getCheckInDate().isBefore(from)).toList();
        if (window.size() < 2) return null;
        long span = ChronoUnit.DAYS.between(window.get(0).getCheckInDate(), latest);
        if (span < WINDOW_DAYS) return null;

        double n = window.size(), sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (BodyCheckIn e : window) {
            double x = ChronoUnit.DAYS.between(from, e.getCheckInDate());
            double y = e.getWeightLbs();
            sumX += x; sumY += y; sumXY += x * y; sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (denominator == 0) return null;
        double slopePerDay = (n * sumXY - sumX * sumY) / denominator;
        return slopePerDay * 7;
    }

    /** Lose, build or maintain: from the latest meal plan, else from the goal weight, else maintain. */
    private String goalType(User user, List<BodyCheckIn> entries) {
        List<SavedMealPlan> plans = savedMealPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        String fitnessGoal = plans.isEmpty() ? null : plans.get(0).getFitnessGoal();
        if (fitnessGoal != null) {
            String g = fitnessGoal.toLowerCase(Locale.ROOT);
            if (g.contains("lose")) return "lose";
            if (g.contains("build") || g.contains("gain")) return "build";
            return "maintain";
        }
        if (user.getGoalWeightLbs() != null && !entries.isEmpty()) {
            double latest = entries.get(entries.size() - 1).getWeightLbs();
            if (user.getGoalWeightLbs() < latest - 1) return "lose";
            if (user.getGoalWeightLbs() > latest + 1) return "build";
        }
        return "maintain";
    }

    private Map<String, Object> composition(List<BodyCheckIn> entries) {
        List<BodyCheckIn> withFat = entries.stream().filter(e -> e.getBodyFatPct() != null).toList();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("hasData", !withFat.isEmpty());
        if (withFat.isEmpty()) return c;
        BodyCheckIn first = withFat.get(0);
        BodyCheckIn latest = withFat.get(withFat.size() - 1);
        double startFat = first.getWeightLbs() * first.getBodyFatPct() / 100;
        double currentFat = latest.getWeightLbs() * latest.getBodyFatPct() / 100;
        c.put("startBodyFat", round1(first.getBodyFatPct()));
        c.put("currentBodyFat", round1(latest.getBodyFatPct()));
        c.put("startFat", round1(startFat));
        c.put("currentFat", round1(currentFat));
        c.put("startLean", round1(first.getWeightLbs() - startFat));
        c.put("currentLean", round1(latest.getWeightLbs() - currentFat));
        c.put("fatChange", round1(currentFat - startFat));
        c.put("leanChange", round1((latest.getWeightLbs() - currentFat) - (first.getWeightLbs() - startFat)));
        c.put("hasChange", withFat.size() >= 2);
        return c;
    }

    /** First week vs most recent week, for each 1 to 5 score. */
    private List<Map<String, Object>> feelings(List<BodyCheckIn> entries) {
        LocalDate firstWeekEnd = entries.get(0).getCheckInDate().plusDays(WINDOW_DAYS - 1);
        LocalDate lastWeekStart = entries.get(entries.size() - 1).getCheckInDate().minusDays(WINDOW_DAYS - 1);
        Map<String, Function<BodyCheckIn, Integer>> scores = new LinkedHashMap<>();
        scores.put("Energy", BodyCheckIn::getEnergy);
        scores.put("Sleep", BodyCheckIn::getSleep);
        scores.put("Hunger", BodyCheckIn::getHunger);
        scores.put("Stress", BodyCheckIn::getStress);
        List<Map<String, Object>> rows = new ArrayList<>();
        scores.forEach((name, getter) -> {
            OptionalDouble start = entries.stream().filter(e -> !e.getCheckInDate().isAfter(firstWeekEnd))
                    .map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).average();
            OptionalDouble current = entries.stream().filter(e -> !e.getCheckInDate().isBefore(lastWeekStart))
                    .map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).average();
            if (start.isEmpty() && current.isEmpty()) return;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("start", start.isPresent() ? round1(start.getAsDouble()) : null);
            row.put("current", current.isPresent() ? round1(current.getAsDouble()) : null);
            // For energy and sleep higher is better; for hunger and stress lower is better.
            row.put("higherIsBetter", name.equals("Energy") || name.equals("Sleep"));
            rows.add(row);
        });
        return rows;
    }

    /** Food-log consistency for the last 7 days and since the first check-in. */
    private Map<String, Object> nutrition(User user, LocalDate today, LocalDate startDate) {
        List<SavedMealPlan> plans = savedMealPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user);
        Integer targetCalories = plans.isEmpty() ? null : plans.get(0).getTargetCalories();
        Integer targetProtein = plans.isEmpty() ? null : plans.get(0).getTargetProtein();

        LocalDate weekStart = today.minusDays(WINDOW_DAYS - 1);
        Map<LocalDate, int[]> byDay = dailyTotals(user, weekStart, today);
        int daysLogged = byDay.size();
        double avgCalories = byDay.values().stream().mapToInt(t -> t[0]).average().orElse(0);
        long proteinHits = targetProtein == null ? 0
                : byDay.values().stream().filter(t -> t[1] >= 0.9 * targetProtein).count();

        Map<String, Object> n = new LinkedHashMap<>();
        n.put("daysLogged", daysLogged);
        n.put("avgCalories", daysLogged == 0 ? null : (int) Math.round(avgCalories));
        n.put("targetCalories", targetCalories);
        n.put("targetProtein", targetProtein);
        n.put("proteinHitDays", proteinHits);
        n.put("daysSinceStart", null);
        n.put("daysLoggedSinceStart", null);
        if (startDate != null && !startDate.isAfter(today)) {
            long elapsed = ChronoUnit.DAYS.between(startDate, today) + 1;
            n.put("daysSinceStart", elapsed);
            n.put("daysLoggedSinceStart", dailyTotals(user, startDate, today).size());
        }
        return n;
    }

    /** date -> {calories, protein}, only for days with at least one entry. */
    private Map<LocalDate, int[]> dailyTotals(User user, LocalDate from, LocalDate to) {
        Map<LocalDate, int[]> byDay = new TreeMap<>();
        for (FoodLog log : foodLogRepository.findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(user, from, to)) {
            int[] totals = byDay.computeIfAbsent(log.getLogDate(), d -> new int[2]);
            totals[0] += log.getCalories();
            totals[1] += log.getProtein();
        }
        return byDay;
    }

    private static Double round1(Double value) {
        if (value == null || value.isNaN()) return null;
        return Math.round(value * 10) / 10.0;
    }
}
