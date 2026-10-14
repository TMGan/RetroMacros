package com.mealplanner.service;

import com.mealplanner.model.FoodLog;
import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.repository.FoodLogRepository;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final SavedMealPlanRepository savedMealPlanRepository;
    private final FoodLogRepository foodLogRepository;
    private final com.mealplanner.repository.SwapHistoryRepository swapHistoryRepository;
    private final com.mealplanner.repository.LearnedUserPreferenceRepository learnedUserPreferenceRepository;
    private final com.mealplanner.repository.UserFoodPreferencesRepository userFoodPreferencesRepository;
    private final com.mealplanner.repository.AiUsageRepository aiUsageRepository;

    public AdminService(UserRepository userRepository,
                        SavedMealPlanRepository savedMealPlanRepository,
                        FoodLogRepository foodLogRepository,
                        com.mealplanner.repository.SwapHistoryRepository swapHistoryRepository,
                        com.mealplanner.repository.LearnedUserPreferenceRepository learnedUserPreferenceRepository,
                        com.mealplanner.repository.UserFoodPreferencesRepository userFoodPreferencesRepository,
                        com.mealplanner.repository.AiUsageRepository aiUsageRepository) {
        this.swapHistoryRepository = swapHistoryRepository;
        this.learnedUserPreferenceRepository = learnedUserPreferenceRepository;
        this.userFoodPreferencesRepository = userFoodPreferencesRepository;
        this.aiUsageRepository = aiUsageRepository;
        this.userRepository = userRepository;
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.foodLogRepository = foodLogRepository;
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalUsers = userRepository.count();
        stats.put("totalUsers", totalUsers);

        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
        long newUsersThisWeek = 0;
        try { newUsersThisWeek = userRepository.countByCreatedAtAfter(weekAgo); } catch (Exception ignore) {}
        stats.put("newUsersThisWeek", newUsersThisWeek);

        long totalPlans = savedMealPlanRepository.count();
        long failedPlans = 0;
        try { failedPlans = savedMealPlanRepository.countByGenerationFailed(true); } catch (Exception ignore) {}
        double successRate = totalPlans > 0 ? ((totalPlans - failedPlans) * 100.0 / totalPlans) : 0;
        stats.put("aiSuccessRate", Math.round(successRate * 10) / 10.0);

        // Estimate Claude API cost (~$0.075 per plan per your guide)
        double estimatedCost = totalPlans * 0.075;
        stats.put("estimatedApiCost", Math.round(estimatedCost * 100) / 100.0);

        Double avgAccuracy = null;
        try { avgAccuracy = savedMealPlanRepository.getAverageAccuracyScore(); } catch (Exception ignore) {}
        stats.put("macroAccuracy", avgAccuracy != null ? Math.round(avgAccuracy * 10) / 10.0 : 0);

        LocalDate today = LocalDate.now();
        long activeToday = 0;
        try { activeToday = foodLogRepository.countActiveUsersOnDate(today); } catch (Exception ignore) {}
        stats.put("activeToday", activeToday);

        long totalFoodLogs = foodLogRepository.count();
        stats.put("totalFoodLogs", totalFoodLogs);

        long mealPlansThisWeek = 0;
        try { mealPlansThisWeek = savedMealPlanRepository.countByCreatedAtAfter(weekAgo); } catch (Exception ignore) {}
        stats.put("mealPlansThisWeek", mealPlansThisWeek);

        return stats;
    }

    public List<Map<String, Object>> getAllUsersWithStats() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> userStats = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("user", user);
            try { stats.put("totalFoodLogs", foodLogRepository.countByUser(user)); } catch (Exception e) { stats.put("totalFoodLogs", 0); }
            try { stats.put("totalMealPlans", savedMealPlanRepository.countByUser(user)); } catch (Exception e) { stats.put("totalMealPlans", 0); }

            FoodLog last = null;
            try { last = foodLogRepository.findTopByUserOrderByLogDateDesc(user); } catch (Exception ignore) {}
            stats.put("lastActive", last != null ? last.getLogDate() : null);

            userStats.add(stats);
        }

        return userStats;
    }

    public Map<String, Object> getUserDetails(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> details = new HashMap<>();
        details.put("user", user);

        List<SavedMealPlan> plans = savedMealPlanRepository.findByUserOrderByCreatedAtDesc(user);
        details.put("mealPlans", plans);
        details.put("totalMealPlans", plans.size());

        List<FoodLog> allFood = foodLogRepository.findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(
                user, LocalDate.of(1970,1,1), LocalDate.now());
        details.put("allFoodLogs", allFood);
        details.put("totalFoodLogs", allFood.size());

        LocalDate weekAgo = LocalDate.now().minusDays(7);
        List<FoodLog> recent = foodLogRepository.findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(user, weekAgo, LocalDate.now());
        details.put("recentFood", recent);

        FoodLog last = null;
        try { last = foodLogRepository.findTopByUserOrderByLogDateDesc(user); } catch (Exception ignore) {}
        details.put("lastActive", last != null ? last.getLogDate() : null);

        double avgAccuracy = plans.stream()
                .filter(p -> p.getAccuracyScore() != null)
                .mapToDouble(SavedMealPlan::getAccuracyScore)
                .average().orElse(0.0);
        details.put("avgAccuracy", Math.round(avgAccuracy * 10) / 10.0);

        long daysActive = allFood.stream().map(FoodLog::getLogDate).distinct().count();
        details.put("daysActive", daysActive);

        return details;
    }

    public Map<String, Object> getAIAccuracyMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        Double avg = null;
        try { avg = savedMealPlanRepository.getAverageAccuracyScore(); } catch (Exception ignore) {}
        metrics.put("overallAccuracy", avg != null ? Math.round(avg * 10) / 10.0 : 0);

        List<SavedMealPlan> inaccurate = Collections.emptyList();
        try { inaccurate = savedMealPlanRepository.findInaccuratePlans(90.0); } catch (Exception ignore) {}
        metrics.put("inaccuratePlans", inaccurate);
        metrics.put("inaccuratePlanCount", inaccurate.size());

        long totalPlans = savedMealPlanRepository.count();
        long failedPlans = 0;
        try { failedPlans = savedMealPlanRepository.countByGenerationFailed(true); } catch (Exception ignore) {}
        double failureRate = totalPlans > 0 ? (failedPlans * 100.0 / totalPlans) : 0;
        metrics.put("failureRate", Math.round(failureRate * 10) / 10.0);

        return metrics;
    }

    public List<User> searchUsers(String query) {
        return userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(query, query);
    }

    /** Deletes a user and every record that belongs to them. */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        // Food logs first: they hold an FK to saved meal plans.
        foodLogRepository.deleteAll(foodLogRepository.findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(
                user, LocalDate.of(1970, 1, 1), LocalDate.now().plusYears(1)));
        swapHistoryRepository.deleteAll(swapHistoryRepository.findByUserOrderBySwapDateDesc(user));
        learnedUserPreferenceRepository.deleteAll(learnedUserPreferenceRepository.findByUserOrderByConfidenceScoreDesc(user));
        userFoodPreferencesRepository.findByUser(user).ifPresent(userFoodPreferencesRepository::delete);
        aiUsageRepository.deleteAll(aiUsageRepository.findByUser(user));
        // Saved meal plans go with the user (orphanRemoval on User.savedMealPlans).
        userRepository.delete(user);
    }
}

