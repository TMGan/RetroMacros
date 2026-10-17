package com.mealplanner.service;

import com.mealplanner.model.LearnedUserPreference;
import com.mealplanner.model.SwapHistory;
import com.mealplanner.model.User;
import com.mealplanner.repository.LearnedUserPreferenceRepository;
import com.mealplanner.repository.SwapHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SwapService {

    private final SwapHistoryRepository swapHistoryRepository;
    private final LearnedUserPreferenceRepository preferenceRepository;

    public SwapService(SwapHistoryRepository swapHistoryRepository,
                       LearnedUserPreferenceRepository preferenceRepository) {
        this.swapHistoryRepository = swapHistoryRepository;
        this.preferenceRepository = preferenceRepository;
    }

    public List<Map<String, Object>> getSwapSuggestions(String originalFood, Map<String, Integer> macros) {
        String category = categorizeFood(originalFood);
        switch (category) {
            case "protein": return getProteinSwaps(macros);
            case "carb": return getCarbSwaps(macros);
            case "veggie": return getVeggieSwaps();
            case "fat": return getFatSwaps(macros);
            default: return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> getProteinSwaps(Map<String, Integer> t) {
        List<Map<String, Object>> swaps = new ArrayList<>();
        swaps.add(create("Chicken breast (6oz)", 280, 53, 0, 6));
        swaps.add(create("Ground turkey (6oz)", 260, 48, 0, 10));
        swaps.add(create("Salmon (6oz)", 340, 40, 0, 20));
        swaps.add(create("Lean ground beef (5oz)", 310, 42, 0, 18));
        swaps.add(create("Tilapia (8oz)", 290, 50, 0, 8));
        return swaps;
    }

    private List<Map<String, Object>> getCarbSwaps(Map<String, Integer> t) {
        List<Map<String, Object>> swaps = new ArrayList<>();
        swaps.add(create("White rice (1 cup)", 200, 4, 45, 0));
        swaps.add(create("Sweet potato (200g)", 180, 4, 41, 0));
        swaps.add(create("Oats (1 cup cooked)", 150, 5, 27, 3));
        swaps.add(create("Whole wheat bread (2 slices)", 160, 8, 28, 2));
        swaps.add(create("Quinoa (1 cup cooked)", 220, 8, 39, 4));
        return swaps;
    }

    private List<Map<String, Object>> getVeggieSwaps() {
        List<Map<String, Object>> swaps = new ArrayList<>();
        swaps.add(create("Broccoli (1 cup)", 31, 3, 6, 0));
        swaps.add(create("Spinach (2 cups)", 14, 2, 2, 0));
        swaps.add(create("Green beans (1 cup)", 31, 2, 7, 0));
        swaps.add(create("Bell peppers (1 cup)", 30, 1, 7, 0));
        swaps.add(create("Asparagus (1 cup)", 27, 3, 5, 0));
        swaps.add(create("Zucchini (1 cup)", 20, 1, 4, 0));
        return swaps;
    }

    private List<Map<String, Object>> getFatSwaps(Map<String, Integer> t) {
        List<Map<String, Object>> swaps = new ArrayList<>();
        swaps.add(create("Avocado (1/2 medium)", 120, 1, 6, 11));
        swaps.add(create("Almonds (1oz)", 160, 6, 6, 14));
        swaps.add(create("Olive oil (1 tbsp)", 120, 0, 0, 14));
        swaps.add(create("Peanut butter (2 tbsp)", 190, 8, 7, 16));
        swaps.add(create("Walnuts (1oz)", 185, 4, 4, 18));
        return swaps;
    }

    private Map<String, Object> create(String name, int cal, int p, int c, int f) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("calories", cal);
        m.put("protein", p);
        m.put("carbs", c);
        m.put("fat", f);
        return m;
    }

    private String categorizeFood(String food) {
        String lower = Optional.ofNullable(food).orElse("").toLowerCase();
        if (lower.matches(".*(chicken|salmon|beef|turkey|fish|egg|protein).*")) return "protein";
        if (lower.matches(".*(rice|potato|oat|bread|pasta|quinoa).*")) return "carb";
        if (lower.matches(".*(broccoli|spinach|pepper|veggie|vegetable|green).*")) return "veggie";
        if (lower.matches(".*(avocado|oil|nut|butter).*")) return "fat";
        return "unknown";
    }

    public void recordSwap(User user, String swapType, String originalFood, String replacementFood, String mealContext) {
        SwapHistory sh = new SwapHistory();
        sh.setUser(user);
        sh.setSwapType(swapType);
        sh.setOriginalFood(originalFood);
        sh.setReplacementFood(replacementFood);
        sh.setMealContext(mealContext);
        sh.setSwapDate(LocalDateTime.now());
        swapHistoryRepository.save(sh);
        updateLearnedPreferences(user, originalFood, replacementFood);
    }

    private void updateLearnedPreferences(User user, String originalFood, String replacementFood) {
        Optional<LearnedUserPreference> prefOpt = preferenceRepository
                .findByUserAndPreferenceTypeAndFoodItemAndComparedToFood(user, "prefers_over", replacementFood, originalFood);
        if (prefOpt.isPresent()) {
            LearnedUserPreference pref = prefOpt.get();
            pref.setTimesObserved(pref.getTimesObserved() + 1);
            BigDecimal newConf = pref.getConfidenceScore().add(new BigDecimal("0.10"));
            if (newConf.compareTo(new BigDecimal("0.95")) > 0) newConf = new BigDecimal("0.95");
            pref.setConfidenceScore(newConf);
            pref.setLastObserved(LocalDateTime.now());
            preferenceRepository.save(pref);
        } else {
            LearnedUserPreference pref = new LearnedUserPreference();
            pref.setUser(user);
            pref.setPreferenceType("prefers_over");
            pref.setFoodItem(replacementFood);
            pref.setComparedToFood(originalFood);
            preferenceRepository.save(pref);
        }

        Optional<LearnedUserPreference> dislike = preferenceRepository
                .findByUserAndPreferenceTypeAndFoodItemAndComparedToFood(user, "dislikes", originalFood, null);
        if (dislike.isPresent()) {
            LearnedUserPreference d = dislike.get();
            d.setTimesObserved(d.getTimesObserved() + 1);
            BigDecimal newConf = d.getConfidenceScore().add(new BigDecimal("0.10"));
            if (newConf.compareTo(new BigDecimal("0.95")) > 0) newConf = new BigDecimal("0.95");
            d.setConfidenceScore(newConf);
            d.setLastObserved(LocalDateTime.now());
            preferenceRepository.save(d);
        } else {
            LearnedUserPreference d = new LearnedUserPreference();
            d.setUser(user);
            d.setPreferenceType("dislikes");
            d.setFoodItem(originalFood);
            preferenceRepository.save(d);
        }
    }

    public String buildLearnedPreferencesPrompt(User user) {
        List<LearnedUserPreference> list = preferenceRepository
                .findByUserAndConfidenceScoreGreaterThanEqual(user, new BigDecimal("0.70"));
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("LEARNED FROM THE CLIENT'S PAST SWAPS:\n");
        for (LearnedUserPreference p : list) {
            if ("prefers_over".equals(p.getPreferenceType())) {
                sb.append(String.format("- Prefers %s over %s (confidence: %.0f%%)\n",
                        p.getFoodItem(), p.getComparedToFood(), p.getConfidenceScore().multiply(new BigDecimal("100")).doubleValue()));
            } else if ("dislikes".equals(p.getPreferenceType())) {
                sb.append(String.format("- Avoid %s (confidence: %.0f%%)\n",
                        p.getFoodItem(), p.getConfidenceScore().multiply(new BigDecimal("100")).doubleValue()));
            }
        }
        sb.append("Lean toward the foods they prefer and away from the ones they swap out.\n");
        return sb.toString();
    }
}

