package com.mealplanner.service;

import com.mealplanner.model.User;
import com.mealplanner.model.UserFoodPreferences;
import com.mealplanner.repository.UserFoodPreferencesRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserFoodPreferencesService {

    private final UserFoodPreferencesRepository preferencesRepository;

    public UserFoodPreferencesService(UserFoodPreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    public UserFoodPreferences getUserPreferences(User user) {
        return preferencesRepository.findByUser(user).orElse(null);
    }

    public UserFoodPreferences savePreferences(User user,
                                               String preferredFoodsText,
                                               String avoidedFoodsText,
                                               String cookingPreference,
                                               String dietaryStyle) {
        UserFoodPreferences prefs = preferencesRepository.findByUser(user)
                .orElse(new UserFoodPreferences());
        prefs.setUser(user);
        prefs.setPreferredFoodsText(cleanFoodList(preferredFoodsText));
        prefs.setAvoidedFoodsText(cleanFoodList(avoidedFoodsText));
        prefs.setCookingPreference(cookingPreference);
        prefs.setDietaryStyle(dietaryStyle);
        return preferencesRepository.save(prefs);
    }

    private String cleanFoodList(String foodList) {
        if (foodList == null || foodList.trim().isEmpty()) return "";
        String[] items = foodList.split("[,\n]+");
        List<String> cleaned = Arrays.stream(items)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return String.join(", ", cleaned);
    }

    public boolean hasPreferences(User user) {
        return preferencesRepository.existsByUser(user);
    }

    public String buildPreferencesPrompt(User user) {
        UserFoodPreferences prefs = getUserPreferences(user);
        // Use whatever the client filled in. Previously everything was dropped
        // unless "preferred foods" had text, including foods they want to avoid.
        if (prefs == null || (isBlank(prefs.getPreferredFoodsText()) && isBlank(prefs.getAvoidedFoodsText())
                && isBlank(prefs.getCookingPreference()) && isBlank(prefs.getDietaryStyle()))) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CLIENT'S FOOD PREFERENCES:\n");
        if (prefs.getPreferredFoodsText() != null && !prefs.getPreferredFoodsText().isEmpty()) {
            sb.append("- Foods they picked as ones they like: ").append(prefs.getPreferredFoodsText()).append("\n");
            sb.append("  Build most meals from these. Repeating their favorites across the days is good; it's their plan. ")
              .append("Add other everyday foods only to round out nutrition (for example a vegetable or a fruit they didn't pick).\n");
        }
        if (prefs.getAvoidedFoodsText() != null && !prefs.getAvoidedFoodsText().isEmpty()) {
            sb.append("- Foods to avoid: ").append(prefs.getAvoidedFoodsText()).append("\n");
        }
        if (prefs.getCookingPreference() != null && !prefs.getCookingPreference().isEmpty()) {
            sb.append("- Cooking preference: ").append(prefs.getCookingPreference()).append("\n");
        }
        if (prefs.getDietaryStyle() != null && !prefs.getDietaryStyle().isEmpty()) {
            sb.append("- Dietary style: ").append(prefs.getDietaryStyle()).append("\n");
        }
        if (!isBlank(prefs.getAvoidedFoodsText())) {
            sb.append("Treat the foods to avoid as a hard rule, the same as an allergy.\n");
        }
        return sb.toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

