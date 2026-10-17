package com.mealplanner.service;

import com.mealplanner.model.Day;
import com.mealplanner.model.FoodItem;
import com.mealplanner.model.Meal;
import com.mealplanner.model.MealPlan;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Checks generated meals against a client's allergies in code, so allergy
 * safety does not rest on the AI following its prompt.
 *
 * Matching is keyword based and errs on the side of flagging: a false alarm
 * costs one regeneration, a miss could hurt someone.
 */
public final class AllergenGuard {

    // Keywords for each allergy option on the plan form.
    private static final Map<String, List<String>> KEYWORDS = Map.of(
            "dairy", List.of("milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "whey", "casein", "ghee", "kefir", "parmesan", "mozzarella", "cheddar", "feta", "ricotta", "custard", "ice cream"),
            "eggs", List.of("egg", "omelet", "omelette", "frittata", "mayonnaise", "mayo", "meringue"),
            "peanuts", List.of("peanut"),
            "tree nuts", List.of("almond", "walnut", "cashew", "pecan", "pistachio", "hazelnut", "macadamia", "brazil nut", "pine nut", "praline", "marzipan", "nutella"),
            "soy", List.of("soy", "tofu", "edamame", "tempeh", "miso", "tamari"),
            "shellfish", List.of("shrimp", "prawn", "crab", "lobster", "scallop", "clam", "mussel", "oyster", "crawfish", "crayfish"),
            "fish", List.of("fish", "salmon", "tuna", "cod", "tilapia", "halibut", "trout", "sardine", "anchov", "mackerel", "haddock", "mahi", "snapper", "swordfish", "bass"),
            "gluten/wheat", List.of("wheat", "bread", "pasta", "spaghetti", "noodle", "flour", "tortilla", "couscous", "barley", "rye", "bagel", "cracker", "pita", "naan", "cereal", "granola", "breadcrumb", "panko", "seitan", "bulgur", "farro", "toast", "bun", "wrap")
    );

    // Phrases that contain a keyword but are safe for that allergy.
    private static final Map<String, List<String>> SAFE_PHRASES = Map.of(
            "dairy", List.of("peanut butter", "almond butter", "cashew butter", "sunflower butter", "nut butter", "cocoa butter",
                    "almond milk", "oat milk", "soy milk", "coconut milk", "rice milk", "cashew milk", "dairy-free", "dairy free",
                    "coconut cream", "cream of tartar"),
            "eggs", List.of("eggplant", "egg-free", "egg free"),
            "gluten/wheat", List.of("gluten-free", "gluten free", "rice noodle", "corn tortilla", "lettuce wrap", "buckwheat",
                    "rice cracker", "rice pasta", "chickpea pasta", "lentil pasta")
    );

    private AllergenGuard() {}

    /** Allergy names (as entered) that the plan appears to contain. Empty means it passed. */
    public static Set<String> violations(Collection<String> allergies, MealPlan plan) {
        Set<String> found = new LinkedHashSet<>();
        if (plan == null || plan.getDays() == null) return found;
        for (Day day : plan.getDays()) {
            if (day.getMeals() == null) continue;
            for (Meal meal : day.getMeals()) {
                found.addAll(violations(allergies, meal));
            }
        }
        return found;
    }

    public static Set<String> violations(Collection<String> allergies, Meal meal) {
        Set<String> found = new LinkedHashSet<>();
        if (allergies == null || allergies.isEmpty() || meal == null) return found;
        String text = mealText(meal);
        for (String allergy : allergies) {
            if (allergy == null || allergy.isBlank()) continue;
            if (contains(text, allergy)) found.add(allergy.trim());
        }
        return found;
    }

    /** For meals that are just a name and an ingredient list, such as random meal ideas. */
    public static Set<String> violations(Collection<String> allergies, String name, Collection<String> ingredients) {
        Set<String> found = new LinkedHashSet<>();
        if (allergies == null || allergies.isEmpty()) return found;
        String text = ((name == null ? "" : name) + " " + String.join(" ", ingredients == null ? List.of() : ingredients))
                .toLowerCase(Locale.ROOT);
        for (String allergy : allergies) {
            if (allergy != null && !allergy.isBlank() && contains(text, allergy)) found.add(allergy.trim());
        }
        return found;
    }

    static boolean contains(String text, String allergy) {
        String key = allergy.trim().toLowerCase(Locale.ROOT);
        String checked = text;
        for (String safe : SAFE_PHRASES.getOrDefault(key, List.of())) {
            checked = checked.replace(safe, " ");
        }
        List<String> keywords = KEYWORDS.get(key);
        if (keywords == null) {
            // Free-text allergy such as "tomatoes": match the word itself, and its singular.
            keywords = List.of(key, singular(key));
        }
        for (String keyword : keywords) {
            if (keyword.isBlank()) continue;
            // Word start only, so "egg" matches "eggs" but "nut" does not match "coconut".
            if (Pattern.compile("\\b" + Pattern.quote(keyword)).matcher(checked).find()) return true;
        }
        return false;
    }

    private static String singular(String word) {
        if (word.endsWith("oes")) return word.substring(0, word.length() - 2);
        if (word.endsWith("ies")) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("s") && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }

    private static String mealText(Meal meal) {
        StringBuilder sb = new StringBuilder();
        sb.append(meal.getName()).append(' ');
        if (meal.getFoods() != null) {
            for (FoodItem food : meal.getFoods()) sb.append(food.getItem()).append(' ');
        }
        if (meal.getRecipe() != null) {
            sb.append(meal.getRecipe().getName()).append(' ');
            if (meal.getRecipe().getIngredients() != null) {
                for (String ingredient : meal.getRecipe().getIngredients()) sb.append(ingredient).append(' ');
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
