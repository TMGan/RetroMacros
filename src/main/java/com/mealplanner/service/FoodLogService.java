package com.mealplanner.service;

import com.mealplanner.model.FoodLog;
import com.mealplanner.model.User;
import com.mealplanner.repository.FoodLogRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FoodLogService {

    private final FoodLogRepository foodLogRepository;
    private final Environment env;

    private final AiClient aiClient;

    public FoodLogService(FoodLogRepository foodLogRepository, Environment env, AiClient aiClient) {
        this.aiClient = aiClient;
        this.foodLogRepository = foodLogRepository;
        this.env = env;
    }

    public FoodLog addFoodLog(FoodLog foodLog) {
        return foodLogRepository.save(foodLog);
    }

    /** The user's saved timezone, or the server's if none has been recorded yet. */
    public java.time.ZoneId zoneFor(User user) {
        try {
            if (user != null && user.getTimezone() != null && !user.getTimezone().isBlank()) {
                return java.time.ZoneId.of(user.getTimezone());
            }
        } catch (Exception ignore) {}
        return java.time.ZoneId.systemDefault();
    }

    /** Today's date where the user is, so evening entries stay on the right day. */
    public LocalDate today(User user) {
        return LocalDate.now(zoneFor(user));
    }

    public List<FoodLog> getTodaysFoodLogs(User user) {
        LocalDate today = today(user);
        return foodLogRepository.findByUserAndLogDateOrderByTimeLoggedAsc(user, today);
    }

    public List<FoodLog> getFoodLogsByDate(User user, LocalDate date) {
        return foodLogRepository.findByUserAndLogDateOrderByTimeLoggedAsc(user, date);
    }

    public List<FoodLog> getLast7DaysFoodLogs(User user) {
        LocalDate today = today(user);
        LocalDate sevenDaysAgo = today.minusDays(6);
        return foodLogRepository.findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(user, sevenDaysAgo, today);
    }

    public Map<String, Integer> calculateTodaysTotals(User user) {
        LocalDate today = today(user);
        return calculateDailyTotals(user, today);
    }

    public Map<String, Integer> calculateDailyTotals(User user, LocalDate date) {
        java.util.List<Object[]> rows = foodLogRepository.calculateDailyTotals(user, date);
        Map<String, Integer> totals = new HashMap<>();
        if (rows != null && !rows.isEmpty()) {
            Object[] result = rows.get(0);
            totals.put("calories", ((Number) result[0]).intValue());
            totals.put("protein", ((Number) result[1]).intValue());
            totals.put("carbs", ((Number) result[2]).intValue());
            totals.put("fat", ((Number) result[3]).intValue());
        } else {
            totals.put("calories", 0);
            totals.put("protein", 0);
            totals.put("carbs", 0);
            totals.put("fat", 0);
        }
        return totals;
    }

    public void deleteFoodLog(Long id) {
        foodLogRepository.deleteById(id);
    }

    public FoodLog updateFoodLog(FoodLog foodLog) {
        return foodLogRepository.save(foodLog);
    }

    public FoodLog getFoodLogById(Long id) {
        return foodLogRepository.findById(id).orElse(null);
    }

    /**
     * Estimate macros from food description using AI.
     * Returns null if invalid or on errors per validation rules.
     */
    public Map<String, Integer> estimateMacrosWithAI(User user, String foodDescription) {
        return estimateMacrosWithAI(user, foodDescription, false);
    }

    /** @param offline when true, never calls the paid API (used for the shared demo account) */
    public Map<String, Integer> estimateMacrosWithAI(User user, String foodDescription, boolean offline) {
        if (foodDescription == null) return null;
        String desc = foodDescription.trim();
        if (desc.isEmpty()) return null;
        if (desc.length() < 3) return null;
        if (desc.length() > 500) return null;

        try {
            // Mock mode for local/dev testing without network/API key
            String mock = env.getProperty("ai.mock", "false");
            if (offline || "true".equalsIgnoreCase(mock)) {
                return estimateMacrosMock(desc);
            }
            String aiResponse = aiClient.complete(user, AiClient.Kind.ESTIMATE, buildMacroEstimationPromptV2(desc));
            return parseMacroResponse(aiResponse);
        } catch (Exception e) {
            System.err.println("Error estimating macros: " + e.getMessage());
            return null;
        }
    }

    // Updated prompt focused on USDA accuracy and precise numeric-only output
    private String buildMacroEstimationPromptV2(String foodDescription) {
        return String.format(
                "You are a professional fitness nutritionist with access to USDA FoodData Central.\n" +
                "Task: Analyze a food description and estimate macros ACCURATELY.\n\n" +
                "RULES:\n" +
                "- Use USDA standards for whole foods; account for cooked vs raw weights.\n" +
                "- Be precise: round to nearest 1g.\n" +
                "- Use realistic portions; if portion unspecified, assume typical serving (e.g., chicken cooked 6oz, rice 1 cup cooked).\n" +
                "- If uncertain, choose conservative estimates (avoid inflated numbers).\n" +
                "- Reject only non-food, offensive, or gibberish inputs.\n\n" +
                "OUTPUT: If invalid, respond EXACTLY: INVALID.\n" +
                "If valid, respond with ONLY: calories,protein,carbs,fat (numbers only, comma-separated).\n\n" +
                "Food: \"%s\"\n\n" +
                "Response:",
                foodDescription
        );
    }

    private Map<String, Integer> parseMacroResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) return null;
        try {
            String content = aiResponse;
            if (content == null || content.trim().isEmpty()) return null;
            if (content.trim().equalsIgnoreCase("INVALID")) return null;

            String[] parts = content.trim().split(",");
            if (parts.length != 4) return null;

            Map<String, Integer> macros = new HashMap<>();
            macros.put("calories", Integer.parseInt(parts[0].trim()));
            macros.put("protein", Integer.parseInt(parts[1].trim()));
            macros.put("carbs", Integer.parseInt(parts[2].trim()));
            macros.put("fat", Integer.parseInt(parts[3].trim()));

            if (macros.get("calories") < 0 || macros.get("calories") > 5000) return null;
            if (macros.get("protein") < 0 || macros.get("protein") > 500) return null;
            if (macros.get("carbs") < 0 || macros.get("carbs") > 1000) return null;
            if (macros.get("fat") < 0 || macros.get("fat") > 500) return null;

            return macros;
        } catch (NumberFormatException e) {
            return null;
        } catch (Exception e) {
            System.err.println("Error parsing macro response: " + e.getMessage());
            return null;
        }
    }

    // Simple mock estimator for dev/testing without external API
    private Map<String, Integer> estimateMacrosMock(String desc) {
        String lower = desc.toLowerCase();
        // Basic invalid content checks
        String[] banned = {"penis","fuck","shit","car","phone","laptop","table","chair","asdf","xxx"};
        for (String b : banned) {
            if (lower.contains(b)) return null;
        }
        if (!lower.matches(".*[a-z].*")) return null; // must contain letters

        int words = Math.max(1, desc.split("\\s+").length);
        int calories = Math.min(800, 120 + words * 80);
        int protein = Math.min(80, words * 8);
        int carbs = Math.min(120, words * 15);
        int fat = Math.min(40, words * 5);

        Map<String, Integer> out = new HashMap<>();
        out.put("calories", calories);
        out.put("protein", protein);
        out.put("carbs", carbs);
        out.put("fat", fat);
        return out;
    }

    public String suggestHealthierAlternative(User user, String originalFood, int calories, int protein, int carbs, int fat) {
        String lower = originalFood.toLowerCase();
        boolean needsAlt = lower.contains("hot pocket") || lower.contains("pizza") || lower.contains("mcdonald")
                || lower.contains("burger king") || lower.contains("taco bell") || lower.contains("fast food")
                || lower.contains("frozen");
        if (!needsAlt) return null;
        if ("true".equalsIgnoreCase(env.getProperty("ai.mock", "false"))) return null;
        try {
            String prompt = buildHealthierAlternativePrompt(originalFood, calories, protein, carbs, fat);
            String suggestion = aiClient.complete(user, AiClient.Kind.ALTERNATIVE, prompt).trim();
            return suggestion.length() > 300 ? suggestion.substring(0, 300) : suggestion;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildHealthierAlternativePrompt(String originalFood, int cal, int p, int c, int f) {
        return String.format(
                "The user ate: \"%s\" (%d cal, %dg P, %dg C, %dg F)\n\n" +
                        "Suggest a healthier whole food alternative with similar macros.\n\n" +
                        "REQUIREMENTS:\n" +
                        "- Similar total calories (within 50 cal)\n" +
                        "- Similar macros (within 10g each)\n" +
                        "- Use whole foods (chicken, fish, eggs, white rice, vegetables, etc.)\n" +
                        "- Be realistic and practical\n" +
                        "- One short sentence suggestion\n\n" +
                        "EXAMPLE:\n" +
                        "Original: Hot Pocket (320 cal, 12g P, 38g C, 12g F)\n" +
                        "Suggestion: Grilled chicken wrap with whole wheat tortilla and veggies\n\n" +
                        "Your suggestion (one sentence only):",
                originalFood, cal, p, c, f
        );
    }

    // --- Random healthy meal suggestion ---
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final java.util.Random RANDOM = new java.util.Random();
    private static final String[] SUGGESTION_PROTEINS = {
            "chicken breast", "ground turkey", "salmon", "lean ground beef", "eggs", "shrimp",
            "Greek yogurt", "pork tenderloin", "tuna", "tofu", "chicken thighs", "steak"
    };
    private static final String[] SUGGESTION_STYLES = {
            "rice bowl", "sheet-pan dinner", "stir-fry", "wrap or sandwich", "skillet meal",
            "big salad", "breakfast-for-dinner", "pasta", "tacos", "no-cook meal"
    };

    /**
     * A meal idea with what goes in it and how to make it.
     * Keys: description, ingredients (List of String), steps (List of String), calories, protein, carbs, fat.
     */
    public Map<String, Object> generateRandomHealthyMeal(User user, int targetCalories) {
        return generateRandomHealthyMeal(user, targetCalories, false);
    }

    public Map<String, Object> generateRandomHealthyMeal(User user, int targetCalories, boolean offline) {
        try {
            String mock = env.getProperty("ai.mock", "false");
            if (offline || "true".equalsIgnoreCase(mock)) {
                return randomMealMock(targetCalories);
            }
            String aiResponse = aiClient.complete(user, AiClient.Kind.SUGGESTION, buildRandomMealPrompt(targetCalories));
            return parseRandomMealText(aiResponse);
        } catch (Exception e) {
            System.err.println("Error generating random meal: " + e.getMessage());
            return null;
        }
    }

    private String buildRandomMealPrompt(int targetCalories) {
        // The model tends to repeat itself on an identical prompt, so vary the brief.
        String protein = SUGGESTION_PROTEINS[RANDOM.nextInt(SUGGESTION_PROTEINS.length)];
        String style = SUGGESTION_STYLES[RANDOM.nextInt(SUGGESTION_STYLES.length)];
        return String.format(
                "Suggest one healthy, balanced meal of about %d calories that a home cook can make in under 30 minutes.\n" +
                "Build it around %s, in the style of a %s. Common grocery-store ingredients only.\n\n" +
                "Respond with ONLY a JSON object. No prose, no code fences. Use exactly these keys:\n" +
                "{\"name\": string, \"ingredients\": [string], \"steps\": [string], " +
                "\"calories\": number, \"protein\": number, \"carbs\": number, \"fat\": number}\n\n" +
                "Rules:\n" +
                "- name: short dish name.\n" +
                "- ingredients: 4 to 8 items, each with an amount, for example \"6 oz chicken breast\".\n" +
                "- steps: 3 to 5 short cooking steps in order.\n" +
                "- calories, protein, carbs, fat: whole numbers for the entire meal (grams for the macros), " +
                "consistent with the ingredient amounts.",
                targetCalories, protein, style);
    }

    // Package-private so the parsing can be tested without calling the API.
    Map<String, Object> parseRandomMealText(String text) {
        try {
            if (text == null) return null;
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(text.substring(start, end + 1));

            String name = node.path("name").asText("").trim();
            List<String> ingredients = readStrings(node.path("ingredients"), 12);
            List<String> steps = readStrings(node.path("steps"), 8);
            int calories = node.path("calories").asInt(-1);
            int protein = node.path("protein").asInt(-1);
            int carbs = node.path("carbs").asInt(-1);
            int fat = node.path("fat").asInt(-1);
            if (name.isEmpty() || ingredients.isEmpty()) return null;
            if (calories < 0 || calories > 5000 || protein < 0 || protein > 500
                    || carbs < 0 || carbs > 1000 || fat < 0 || fat > 500) return null;

            return meal(name.length() > 120 ? name.substring(0, 120) : name, ingredients, steps, calories, protein, carbs, fat);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readStrings(com.fasterxml.jackson.databind.JsonNode array, int max) {
        List<String> out = new java.util.ArrayList<>();
        if (array == null || !array.isArray()) return out;
        for (com.fasterxml.jackson.databind.JsonNode n : array) {
            String s = n.asText("").trim();
            if (s.isEmpty()) continue;
            out.add(s.length() > 200 ? s.substring(0, 200) : s);
            if (out.size() == max) break;
        }
        return out;
    }

    private Map<String, Object> meal(String name, List<String> ingredients, List<String> steps,
                                     int calories, int protein, int carbs, int fat) {
        Map<String, Object> meal = new HashMap<>();
        meal.put("description", name);
        meal.put("ingredients", ingredients);
        meal.put("steps", steps);
        meal.put("calories", calories);
        meal.put("protein", protein);
        meal.put("carbs", carbs);
        meal.put("fat", fat);
        return meal;
    }

    // Built-in ideas used when AI is off (demo account, ai.mock=true). Picks from the
    // ones closest to the calorie target so repeated clicks still vary.
    private Map<String, Object> randomMealMock(int targetCalories) {
        List<Map<String, Object>> options = new java.util.ArrayList<>(List.of(
                meal("Chicken and Rice Bowl",
                        List.of("6 oz chicken breast", "1 cup cooked white rice", "1 cup broccoli florets", "1 tsp olive oil", "1 tbsp soy sauce", "Salt, pepper, garlic powder"),
                        List.of("Season the chicken with salt, pepper and garlic powder.",
                                "Cook in the olive oil over medium-high heat, 5 to 6 minutes per side, until cooked through. Rest, then slice.",
                                "Steam or microwave the broccoli for 3 minutes.",
                                "Serve chicken and broccoli over the rice and drizzle with soy sauce."),
                        520, 52, 52, 9),
                meal("Salmon with Sweet Potato and Asparagus",
                        List.of("5 oz salmon fillet", "1 medium sweet potato, cubed", "8 asparagus spears", "2 tsp olive oil", "Lemon wedge", "Salt and pepper"),
                        List.of("Heat the oven to 425F. Toss the sweet potato with half the oil and roast for 10 minutes.",
                                "Add the salmon and asparagus to the pan, brush with the rest of the oil, season.",
                                "Roast 12 minutes more, until the salmon flakes.",
                                "Squeeze the lemon over everything."),
                        510, 34, 34, 25),
                meal("Turkey Taco Skillet",
                        List.of("5 oz lean ground turkey (93/7)", "1/2 cup black beans, rinsed", "1/2 cup cooked rice", "1/4 cup salsa", "1 oz shredded cheese", "1 tsp taco seasoning"),
                        List.of("Brown the turkey in a skillet over medium-high heat, breaking it up, about 6 minutes.",
                                "Stir in the taco seasoning, beans, rice and salsa. Heat through for 3 minutes.",
                                "Top with the cheese, cover for 1 minute to melt."),
                        560, 44, 48, 20),
                meal("Greek Yogurt Power Bowl",
                        List.of("1 cup plain nonfat Greek yogurt", "1/2 cup granola", "1/2 cup blueberries", "1 tbsp honey", "1 tbsp chopped almonds"),
                        List.of("Spoon the yogurt into a bowl.",
                                "Top with granola, blueberries and almonds.",
                                "Drizzle with the honey."),
                        450, 30, 62, 10),
                meal("Spinach Omelette with Toast",
                        List.of("3 large eggs", "1 cup baby spinach", "1 oz shredded cheese", "1 slice whole wheat toast", "1 tsp butter", "Salt and pepper"),
                        List.of("Melt the butter in a nonstick pan over medium heat and wilt the spinach, 1 minute.",
                                "Beat the eggs with salt and pepper and pour over the spinach.",
                                "When the edges set, add the cheese, fold in half and cook 1 more minute.",
                                "Serve with the toast."),
                        430, 30, 16, 27),
                meal("Beef and Veggie Stir-Fry",
                        List.of("5 oz flank steak, thinly sliced", "1 cup bell pepper and snap peas", "3/4 cup cooked jasmine rice", "1 tbsp soy sauce", "1 tsp sesame oil", "1 clove garlic, minced"),
                        List.of("Heat the sesame oil in a hot skillet or wok. Sear the steak 2 minutes, then set aside.",
                                "Stir-fry the vegetables and garlic for 3 minutes.",
                                "Return the steak, add the soy sauce and toss for 1 minute.",
                                "Serve over the rice."),
                        540, 38, 46, 20),
                meal("Tuna Salad Wrap",
                        List.of("1 can (5 oz) tuna in water, drained", "2 tbsp plain Greek yogurt", "1 celery stalk, diced", "1 large whole wheat tortilla", "1 cup lettuce", "1 small apple on the side"),
                        List.of("Mix the tuna, yogurt and celery. Season with salt and pepper.",
                                "Pile the lettuce and tuna onto the tortilla.",
                                "Fold in the sides and roll up. Eat with the apple."),
                        420, 38, 50, 7)
        ));
        options.sort(java.util.Comparator.comparingInt(
                (Map<String, Object> m) -> Math.abs((Integer) m.get("calories") - targetCalories)));
        return options.get(RANDOM.nextInt(4));
    }
}
