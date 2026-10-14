package com.mealplanner.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealplanner.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MealPlanService {

    private final AiClient aiClient;
    private final ObjectMapper mapper;
    private final boolean mockMode;
    private final boolean repairEnabled;

    public MealPlanService(AiClient aiClient,
                           @Value("${ai.mock:false}") boolean mockMode,
                           @Value("${ai.repair.enabled:true}") boolean repairEnabled) {
        this.aiClient = aiClient;
        this.mockMode = mockMode;
        this.repairEnabled = repairEnabled;

        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    public MealPlan generateMealPlan(User user, UserProfile profile, MacroTargets targets) throws RuntimeException {
        return generateMealPlan(user, profile, targets, null);
    }

    /** @param user who the plan is for; AI usage and cost are recorded against them */
    public MealPlan generateMealPlan(User user, UserProfile profile, MacroTargets targets, String extraPrompt) throws RuntimeException {
        if (mockMode) {
            return generateMockMealPlan(profile, targets);
        }
        String base = buildPromptV2(profile, targets);
        String prompt = (extraPrompt != null && !extraPrompt.isBlank()) ? (base + "\n\n" + extraPrompt) : base;
        String raw = aiClient.complete(user, AiClient.Kind.PLAN, prompt);
        try {
            String json = sanitizeToJson(raw);
            MealPlan plan = parseAIResponse(json, targets);
            fillMissingDailyTotals(plan, targets);
            return plan;
        } catch (RuntimeException ex) {
            if (!repairEnabled) throw ex;
            String repaired = repairJsonWithAI(user, raw, targets);
            String sanitized = sanitizeToJson(repaired);
            MealPlan plan = parseAIResponse(sanitized, targets);
            fillMissingDailyTotals(plan, targets);
            return plan;
        }
    }

    public boolean isMockMode() {
        return mockMode;
    }

    /** Built-in offline plan. Never calls the paid API, whatever ai.mock is set to. */
    public MealPlan generateSampleMealPlan(UserProfile profile, MacroTargets targets) {
        return generateMockMealPlan(profile, targets);
    }

    public GroceryList generateGroceryList(MealPlan plan) {
        Map<String, Map<String, Map<String, Double>>> agg = new LinkedHashMap<>();
        // category -> item -> unit -> totalQuantity
        for (Day day : plan.getDays()) {
            if (day.getMeals() == null) continue;
            for (Meal meal : day.getMeals()) {
                if (meal.getFoods() == null) continue;
                for (FoodItem fi : meal.getFoods()) {
                    String rawItem = safe(fi.getItem());
                    String item = canonicalizeItem(rawItem);
                    String portion = safe(fi.getPortion());
                    String category = classifyItem(item);
                    ParsedPortion parsed = parsePortion(portion);
                    String unit = normalizeUnit(parsed.unit, item, category);
                    agg.computeIfAbsent(category, k -> new LinkedHashMap<>())
                            .computeIfAbsent(item, k -> new LinkedHashMap<>())
                            .merge(unit, parsed.quantity, Double::sum);
                }
            }
        }

        Map<String, List<String>> lists = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Map<String, Double>>> cat : agg.entrySet()) {
            String category = cat.getKey();
            Map<String, Map<String, Double>> reduced = reduceCategory(category, cat.getValue());
            List<String> items = new ArrayList<>();
            for (Map.Entry<String, Map<String, Double>> it : reduced.entrySet()) {
                if (it.getValue().isEmpty()) {
                    items.add(it.getKey());
                } else {
                    for (Map.Entry<String, Double> unit : it.getValue().entrySet()) {
                        items.add(formatPackagedItem(category, it.getKey(), unit.getKey(), unit.getValue()));
                    }
                }
            }
            lists.put(category, items);
        }

        return new GroceryList(lists);
    }

    private String repairJsonWithAI(User user, String badOutput, MacroTargets targets) {
        String schema = "Return a VALID JSON object with this structure: {\\n  \"days\": [ { \"day\": number, \"meals\": [ { \"name\": string, \"foods\": [ {\"item\": string, \"portion\": string} ], \"macros\": { \"calories\": number, \"protein\": number, \"carbs\": number, \"fat\": number }, \"recipe\": { \"name\": string, \"ingredients\": [string], \"instructions\": [string], \"prepTime\": string, \"cookTime\": string, \"totalTime\": string } } ], \"dailyTotals\": { \"calories\": number, \"protein\": number, \"carbs\": number, \"fat\": number } } ] }";
        String repairPrompt = "You returned content that was not valid JSON for the required schema.\n" +
                "Fix it now by outputting ONLY a valid JSON object matching the schema. No prose. No code fences.\n\n" +
                schema + "\n\nHere is the content to fix:\n" + badOutput;
        String response = aiClient.complete(user, AiClient.Kind.PLAN_REPAIR, repairPrompt);
        return response;
    }

    private void fillMissingDailyTotals(MealPlan plan, MacroTargets targets) {
        if (plan == null || plan.getDays() == null) return;
        for (Day d : plan.getDays()) {
            if (d.getDailyTotal() == null) {
                int cal = 0, p = 0, c = 0, f = 0;
                if (d.getMeals() != null) {
                    for (Meal m : d.getMeals()) {
                        if (m.getMacros() != null) {
                            cal += m.getMacros().getCalories();
                            p += m.getMacros().getProtein();
                            c += m.getMacros().getCarbs();
                            f += m.getMacros().getFat();
                        }
                    }
                }
                if (cal == 0 && p == 0 && c == 0 && f == 0 && targets != null) {
                    d.setDailyTotal(new MacroTargets(targets.getCalories(), targets.getProtein(), targets.getCarbs(), targets.getFat()));
                } else {
                    d.setDailyTotal(new MacroTargets(cal, p, c, f));
                }
            }
        }
    }

    // New nutritionist-mode prompt with whole-food focus, tight macro accuracy, and variety constraints.
    private String buildPromptV2(UserProfile profile, MacroTargets targets) {
        String allergies = (profile.getAllergies() == null || profile.getAllergies().isEmpty())
                ? "None" : String.join(", ", profile.getAllergies());

        String persona = "You are a professional fitness nutritionist and chef with macro-tracking expertise.\n" +
                "CRITICAL RULES FOR MACRO CALCULATIONS:\n" +
                "1) Use USDA FoodData Central standards for whole foods.\n" +
                "2) Be precise: round macros to the nearest 1g.\n" +
                "3) Account for cooking method (raw vs cooked weights).\n" +
                "4) Realistic portions: chicken breast 6-8oz cooked; fish 5-7oz; eggs 1 large = 70 cal/6g P/5g F; rice 1 cup cooked ~200 cal/45g C; sweet potato medium (5oz) ~110 cal/26g C.\n" +
                "5) If numbers seem off, recalc before responding; prioritize accuracy.\n\n" +
                "MEAL PHILOSOPHY:\n" +
                "- Prioritize whole, minimally processed foods.\n" +
                "- Keep meals exciting and flavorful; vary cooking techniques and ingredients.\n" +
                "- Respect allergies and user preferences.\n\n" +
                "VARIETY REQUIREMENTS (3 days):\n" +
                "- No meal may repeat across all days.\n" +
                "- Each day features a different primary protein:\n  * Day 1: Chicken or Turkey\n  * Day 2: Fish or Seafood\n  * Day 3: Beef or Pork or Eggs\n" +
                "- Rotate breakfasts (e.g., eggs → oats → yogurt parfait).\n" +
                "- Rotate vegetables and grains; vary cooking methods.\n\n" +
                "OUTPUT: Return ONLY a valid JSON object (no prose, no code fences).";

        String user = "USER PROFILE:\n" +
                "- Daily Calorie Target: " + targets.getCalories() + "\n" +
                "- Daily Protein Target: " + targets.getProtein() + "g\n" +
                "- Daily Carb Target: " + targets.getCarbs() + "g\n" +
                "- Daily Fat Target: " + targets.getFat() + "g\n" +
                "- Fitness Goal: " + profile.getFitnessGoal() + "\n" +
                "- Allergies/Restrictions: " + allergies + "\n\n" +
                "TARGET MACROS (strict per-day adherence):\n" +
                "- Daily Calories: " + targets.getCalories() + " (match as closely as possible)\n" +
                "- Daily Protein: " + targets.getProtein() + "g (±5g)\n" +
                "- Daily Carbs: " + targets.getCarbs() + "g (±5g)\n" +
                "- Daily Fat: " + targets.getFat() + "g (±3g)\n\n";

        String schema = "SCHEMA (exact keys):\n" +
                "{\\n  \"days\": [\\n    {\\n      \"day\": number,\\n      \"meals\": [\\n        {\\n          \"name\\\": string,\\n          \"foods\\\": [ { \\\"item\\\": string, \\\"portion\\\": string } ],\\n          \"macros\\\": { \\\"calories\\\": number, \\\"protein\\\": number, \\\"carbs\\\": number, \\\"fat\\\": number },\\n          \"recipe\\\": { \\\"name\\\": string, \\\"ingredients\\\": [string], \\\"instructions\\\": [string], \\\"prepTime\\\": string, \\\"cookTime\\\": string, \\\"totalTime\\\": string }\\n        }\\n      ],\\n      \"dailyTotals\\\": { \\\"calories\\\": number, \\\"protein\\\": number, \\\"carbs\\\": number, \\\"fat\\\": number }\\n    }\\n  ]\\n}";

        String guide = "GENERATION INSTRUCTIONS:\n" +
                "- Generate a complete 3-day plan. Each day: 3-4 meals + 1 snack.\n" +
                "- Hit daily targets within ±50 calories; keep macro totals coherent.\n" +
                "- Each meal must include a practical recipe with ingredients (quantities) and 3-5 clear steps.\n" +
                "- Use whole ingredients; keep recipes flavorful and efficient; include prep/cook/total time.\n" +
                "- Output strictly as JSON per schema.\n";

        return persona + "\n\n" + user + schema + "\n\n" + guide;
    }

    // --- Single-meal generation for Swap ---
    public Meal generateReplacementMeal(User user, MacroTargets target, String avoidSimilarTo) {
        String avoid = (avoidSimilarTo == null || avoidSimilarTo.isBlank()) ? "" : ("Avoid making anything similar to: " + avoidSimilarTo + "\n");
        String prompt = String.format("""
                You are a professional fitness nutritionist and chef. Generate ONE different meal that fits these macros closely.

                TARGET MACROS (± small tolerance):
                - Calories: %d (±40)
                - Protein: %dg (±5)
                - Carbs: %dg (±8)
                - Fat: %dg (±5)

                RULES:
                - Prioritize whole, minimally processed foods and exciting but practical flavors.
                - Use realistic portions and account for cooked weights.
                - Include a simple, practical recipe with quantities and 3-5 clear steps.
                - %sReturn ONLY a valid JSON object matching this schema (no prose, no code fences):
                {
                  "name": string, // meal type or short label
                  "foods": [ {"item": string, "portion": string} ],
                  "macros": { "calories": number, "protein": number, "carbs": number, "fat": number },
                  "recipe": { "name": string, "ingredients": [string], "instructions": [string], "prepTime": string, "cookTime": string, "totalTime": string }
                }
                """,
                target.getCalories(), target.getProtein(), target.getCarbs(), target.getFat(), avoid);

        String raw = aiClient.complete(user, AiClient.Kind.SWAP, prompt);
        String json = sanitizeToJson(raw);
        return parseSingleMeal(json);
    }

    private Meal parseSingleMeal(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            Meal m = new Meal();
            m.setName(asText(root.get("name"), List.of("name"), "Meal"));
            // macros
            com.fasterxml.jackson.databind.JsonNode macros = root.get("macros");
            MacroTargets mt = new MacroTargets(
                    asInt(macros, List.of("calories", "kcal"), 0),
                    asInt(macros, List.of("protein", "protein_g"), 0),
                    asInt(macros, List.of("carbs", "carbohydrates", "carbs_g"), 0),
                    asInt(macros, List.of("fat", "fat_g"), 0)
            );
            m.setMacros(mt);
            // foods
            List<FoodItem> foods = new ArrayList<>();
            com.fasterxml.jackson.databind.JsonNode foodsNode = root.get("foods");
            if (foodsNode != null && foodsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode f : foodsNode) {
                    String item = asText(f.get("item"), List.of("item", "name"), "");
                    String portion = asText(f.get("portion"), List.of("portion", "amount"), "");
                    if (!item.isBlank()) foods.add(new FoodItem(item, portion));
                }
            }
            m.setFoods(foods);
            // recipe
            com.fasterxml.jackson.databind.JsonNode r = root.get("recipe");
            if (r != null && !r.isNull()) {
                com.mealplanner.model.Recipe rec = new com.mealplanner.model.Recipe();
                rec.setName(asText(r.get("name"), List.of("name", "title"), m.getName()));
                List<String> ings = new ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode ri = r.get("ingredients");
                if (ri != null && ri.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode x : ri) ings.add(x.asText());
                }
                rec.setIngredients(ings);
                List<String> instr = new ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode rs = r.get("instructions");
                if (rs != null && rs.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode x : rs) instr.add(x.asText());
                }
                rec.setInstructions(instr);
                rec.setPrepTime(asText(r.get("prepTime"), List.of("prepTime"), ""));
                rec.setCookTime(asText(r.get("cookTime"), List.of("cookTime"), ""));
                rec.setTotalTime(asText(r.get("totalTime"), List.of("totalTime"), ""));
                m.setRecipe(rec);
            }
            return m;
        } catch (Exception e) {
            throw new RuntimeException("Invalid single-meal JSON", e);
        }
    }

    // --- Mock plan generation (for offline dev) ---
    private MealPlan generateMockMealPlan(UserProfile profile, MacroTargets targets) {
        MealPlan plan = new MealPlan();
        plan.setDailyTargets(targets);

        List<Day> days = new ArrayList<>();
        for (int d = 1; d <= 3; d++) {
            Day day = new Day();
            day.setDayNumber(d);

            // Split macros roughly: 25% breakfast, 30% lunch, 15% snack, 30% dinner
            MacroTargets b = portion(targets, 0.25);
            MacroTargets l = portion(targets, 0.30);
            MacroTargets s = portion(targets, 0.15);
            MacroTargets dn = portion(targets, 0.30);

            List<Meal> meals = new ArrayList<>();
            meals.add(meal("Breakfast", List.of(
                    new FoodItem("Scrambled eggs", "3 large"),
                    new FoodItem("Oatmeal", "1 cup cooked"),
                    new FoodItem("Banana", "1 medium"),
                    new FoodItem("Whole milk", "1 cup")
            ), b));

            meals.add(meal("Lunch", List.of(
                    new FoodItem("Grilled chicken breast", "6 oz"),
                    new FoodItem("Brown rice", "1.5 cups cooked"),
                    new FoodItem("Broccoli", "1 cup"),
                    new FoodItem("Olive oil", "1 tbsp")
            ), l));

            meals.add(meal("Snack", List.of(
                    new FoodItem("Greek yogurt (plain)", "1 cup"),
                    new FoodItem("Mixed berries", "1 cup"),
                    new FoodItem("Almond butter", "1 tbsp")
            ), s));

            meals.add(meal("Dinner", List.of(
                    new FoodItem("Grilled salmon", "6 oz"),
                    new FoodItem("Sweet potato", "1 large"),
                    new FoodItem("Mixed vegetables", "2 cups"),
                    new FoodItem("Olive oil", "1 tbsp")
            ), dn));

            day.setMeals(meals);
            day.setDailyTotal(targets);
            days.add(day);
        }

        plan.setDays(days);
        return plan;
    }

    private Meal meal(String name, List<FoodItem> foods, MacroTargets macros) {
        Meal m = new Meal();
        m.setName(name);
        m.setFoods(foods);
        m.setMacros(macros);
        // Provide a simple placeholder recipe in mock mode
        com.mealplanner.model.Recipe r = new com.mealplanner.model.Recipe();
        r.setName(name + " – Simple Prep");
        java.util.List<String> ing = new java.util.ArrayList<>();
        for (FoodItem fi : foods) { ing.add(fi.getPortion() + " " + fi.getItem()); }
        r.setIngredients(ing);
        r.setInstructions(java.util.List.of(
                "Gather ingredients",
                "Cook proteins as you like",
                "Cook grains per package",
                "Steam or microwave veggies",
                "Plate and season to taste"
        ));
        r.setPrepTime("5 mins");
        r.setCookTime("15 mins");
        r.setTotalTime("20 mins");
        m.setRecipe(r);
        return m;
    }

    private MacroTargets portion(MacroTargets total, double fraction) {
        int c = (int) Math.round(total.getCalories() * fraction);
        int p = (int) Math.round(total.getProtein() * fraction);
        int carbs = (int) Math.round(total.getCarbs() * fraction);
        int f = (int) Math.round(total.getFat() * fraction);
        return new MacroTargets(c, p, carbs, f);
    }

    // --- Parsing ---
    private MealPlan parseAIResponse(String json, MacroTargets targets) {
        try {
            MealPlanDTO dto = mapper.readValue(json, MealPlanDTO.class);
            return toDomain(dto, targets);
        } catch (Exception e1) {
            try {
                // Lenient tree parsing as fallback
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
                return parseLenient(root, targets);
            } catch (Exception e2) {
                String snippet = json == null ? "" : json.substring(0, Math.min(300, json.length()));
                throw new RuntimeException("Invalid AI JSON response: " + snippet, e2);
            }
        }
    }

    private MealPlan toDomain(MealPlanDTO dto, MacroTargets targets) {
        MealPlan plan = new MealPlan();
        plan.setDailyTargets(targets);
        List<Day> days = new ArrayList<>();
        if (dto != null && dto.days != null) {
            for (DayDTO d : dto.days) {
                Day day = new Day();
                day.setDayNumber(d.day);
                day.setDailyTotal(toMacroTargets(d.dailyTotals));
                List<Meal> meals = new ArrayList<>();
                if (d.meals != null) {
                    for (MealDTO m : d.meals) {
                        Meal meal = new Meal();
                        meal.setName(m.name);
                        meal.setMacros(toMacroTargets(m.macros));
                        if (m.recipe != null) {
                            com.mealplanner.model.Recipe r = new com.mealplanner.model.Recipe();
                            r.setName(m.recipe.name);
                            r.setIngredients(m.recipe.ingredients);
                            r.setInstructions(m.recipe.instructions);
                            r.setPrepTime(m.recipe.prepTime);
                            r.setCookTime(m.recipe.cookTime);
                            r.setTotalTime(m.recipe.totalTime);
                            meal.setRecipe(r);
                        }
                        List<FoodItem> foods = new ArrayList<>();
                        if (m.foods != null) {
                            for (FoodItemDTO f : m.foods) {
                                foods.add(new FoodItem(f.item, f.portion));
                            }
                        }
                        meal.setFoods(foods);
                        meals.add(meal);
                    }
                }
                day.setMeals(meals);
                days.add(day);
            }
        }
        plan.setDays(days);
        return plan;
    }

    private MealPlan parseLenient(com.fasterxml.jackson.databind.JsonNode root, MacroTargets targets) {
        MealPlan plan = new MealPlan();
        plan.setDailyTargets(targets);
        List<Day> days = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode daysNode = root.path("days");
        if (daysNode.isMissingNode()) {
            // try alternative wrappers
            daysNode = root.path("plan");
        }
        if (daysNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode d : daysNode) {
                Day day = new Day();
                int dayNum = asInt(d, List.of("day", "dayNumber", "index", "day_index"), days.size() + 1);
                day.setDayNumber(dayNum);
                com.fasterxml.jackson.databind.JsonNode dailyTotals = firstNonNull(d, List.of("dailyTotals", "dailyTotal", "totals"));
                day.setDailyTotal(nodeToMacros(dailyTotals));
                List<Meal> meals = new ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode mealsNode = d.path("meals");
                if (mealsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode m : mealsNode) {
                        Meal meal = new Meal();
                        meal.setName(asText(m, List.of("name", "meal", "title"), "Meal"));
                        meal.setMacros(nodeToMacros(firstNonNull(m, List.of("macros", "macro", "nutrients"))));
                        // Optional recipe object
                        com.fasterxml.jackson.databind.JsonNode rnode = firstNonNull(m, List.of("recipe"));
                        if (rnode != null && !rnode.isMissingNode()) {
                            com.mealplanner.model.Recipe r = new com.mealplanner.model.Recipe();
                            r.setName(asText(rnode, List.of("name", "title"), null));
                            java.util.List<String> ing = new java.util.ArrayList<>();
                            com.fasterxml.jackson.databind.JsonNode ings = rnode.path("ingredients");
                            if (ings.isArray()) { ings.forEach(n -> ing.add(n.asText())); }
                            r.setIngredients(ing);
                            java.util.List<String> steps = new java.util.ArrayList<>();
                            com.fasterxml.jackson.databind.JsonNode instr = rnode.path("instructions");
                            if (instr.isArray()) { instr.forEach(n -> steps.add(n.asText())); }
                            r.setInstructions(steps);
                            r.setPrepTime(asText(rnode, List.of("prepTime"), null));
                            r.setCookTime(asText(rnode, List.of("cookTime"), null));
                            r.setTotalTime(asText(rnode, List.of("totalTime"), null));
                            meal.setRecipe(r);
                        }
                        List<FoodItem> foods = new ArrayList<>();
                        com.fasterxml.jackson.databind.JsonNode foodsNode = firstNonNull(m, List.of("foods", "items", "ingredients"));
                        if (foodsNode != null && foodsNode.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode f : foodsNode) {
                                if (f.isTextual()) {
                                    foods.add(new FoodItem(f.asText(), ""));
                                } else {
                                    String item = asText(f, List.of("item", "name", "ingredient"), "");
                                    String portion = asText(f, List.of("portion", "quantity", "amount"), "");
                                    foods.add(new FoodItem(item, portion));
                                }
                            }
                        }
                        meal.setFoods(foods);
                        meals.add(meal);
                    }
                }
                day.setMeals(meals);
                days.add(day);
            }
        }
        plan.setDays(days);
        return plan;
    }

    private MacroTargets nodeToMacros(com.fasterxml.jackson.databind.JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        int cal = asInt(n, List.of("calories", "kcals", "kcal"), 0);
        int p = asInt(n, List.of("protein", "proteins"), 0);
        int c = asInt(n, List.of("carbs", "carbohydrates"), 0);
        int f = asInt(n, List.of("fat", "fats"), 0);
        return new MacroTargets(cal, p, c, f);
    }

    private com.fasterxml.jackson.databind.JsonNode firstNonNull(com.fasterxml.jackson.databind.JsonNode parent, List<String> keys) {
        if (parent == null) return null;
        for (String k : keys) {
            com.fasterxml.jackson.databind.JsonNode n = parent.get(k);
            if (n != null && !n.isNull()) return n;
        }
        return null;
    }

    private String asText(com.fasterxml.jackson.databind.JsonNode n, List<String> keys, String def) {
        if (n == null) return def;
        if (n.isTextual()) return n.asText();
        for (String k : keys) {
            com.fasterxml.jackson.databind.JsonNode c = n.get(k);
            if (c != null && !c.isNull()) return c.asText();
        }
        return def;
    }

    private int asInt(com.fasterxml.jackson.databind.JsonNode n, List<String> keys, int def) {
        if (n == null) return def;
        if (n.isNumber()) return n.asInt();
        for (String k : keys) {
            com.fasterxml.jackson.databind.JsonNode c = n.get(k);
            if (c != null && c.isNumber()) return c.asInt();
            if (c != null && c.isTextual()) {
                try { return Integer.parseInt(c.asText().replaceAll("[^0-9-]", "")); } catch (Exception ignored) {}
            }
        }
        return def;
    }

    private String sanitizeToJson(String text) {
        if (text == null) return "{}";
        String t = text.trim();
        // remove code fences if present
        if (t.startsWith("```")) {
            int first = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (first >= 0 && lastFence > first) {
                t = t.substring(first + 1, lastFence).trim();
            }
        }
        // extract the largest {...} block
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1);
        }
        // attempt to auto-close missing brackets/braces in truncated output
        t = balanceBrackets(t);
        // replace smart quotes
        t = t.replace('\u201c', '"').replace('\u201d', '"').replace('\u2019', '\'');
        return t;
    }

    // try to fix truncated JSON by appending expected closing chars
    private String balanceBrackets(String s) {
        if (s == null || s.isBlank()) return s;
        java.util.Deque<Character> st = new java.util.ArrayDeque<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') st.push('}');
            else if (c == '[') st.push(']');
            else if ((c == '}' || c == ']') && !st.isEmpty()) st.pop();
        }
        StringBuilder sb = new StringBuilder(s);
        while (!st.isEmpty()) sb.append(st.pop());
        return sb.toString();
    }

    private MacroTargets toMacroTargets(MacroTargetsDTO m) {
        if (m == null) return null;
        return new MacroTargets(m.calories, m.protein, m.carbs, m.fat);
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    // --- Portion parsing & categorization ---
    private record ParsedPortion(double quantity, String unit) {}

    private ParsedPortion parsePortion(String portion) {
        if (portion == null || portion.isBlank()) return new ParsedPortion(0.0, "");
        // Simple pattern: number (possibly decimal) followed by unit words
        Pattern p = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]+(?:\\s*[a-zA-Z]+)?)?");
        Matcher m = p.matcher(portion.toLowerCase());
        if (m.find()) {
            try {
                double qty = Double.parseDouble(m.group(1));
                String unit = Optional.ofNullable(m.group(2)).orElse("").trim();
                return new ParsedPortion(qty, unit);
            } catch (NumberFormatException ignored) {}
        }
        return new ParsedPortion(0.0, "");
    }

    private String formatQty(double q) {
        if (Math.abs(q - Math.round(q)) < 1e-6) {
            return String.valueOf((long) Math.round(q));
        }
        return String.format(Locale.US, "%.2f", q);
    }

    private String classifyItem(String item) {
        String s = item.toLowerCase(Locale.US);
        if (containsAny(s, List.of("chicken", "turkey", "beef", "pork", "salmon", "tuna", "cod", "tilapia", "shrimp", "egg"))) {
            return "Proteins";
        }
        if (containsAny(s, List.of("milk", "yogurt", "greek yogurt", "cottage", "cheese", "dairy"))) {
            return "Dairy";
        }
        if (containsAny(s, List.of("broccoli", "spinach", "kale", "asparagus", "pepper", "tomato", "carrot", "cauliflower", "brussels", "bean", "zucchini", "cucumber", "banana", "apple", "berry", "orange", "grape", "watermelon"))) {
            return "Produce";
        }
        if (containsAny(s, List.of("rice", "quinoa", "oat", "bread", "pasta", "potato", "sweet potato"))) {
            return "Grains";
        }
        if (containsAny(s, List.of("olive oil", "oil", "salt", "pepper", "garlic", "onion", "spice", "butter", "almond", "peanut", "nut"))) {
            return "Pantry";
        }
        return "Other";
    }

    private boolean containsAny(String s, List<String> needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    // Present user-friendly packaged items
    private String formatPackagedItem(String category, String itemName, String unit, double qty) {
        String item = itemName.toLowerCase(Locale.US);
        String niceItem = itemName;
        String c = category;
        // Eggs → cartons of 12
        if (item.contains("egg")) {
            double count = unit.equals("count") ? qty : qty; // assume already count
            long cartons = (long) Math.ceil(count / 12.0);
            return pluralize("Egg", cartons) + ": " + cartons + " " + pluralizeWord("carton", cartons) + " (12 each)";
        }
        // Milk → gallons (16 cups)
        if (item.contains("milk")) {
            double cups = unit.equals("cup") ? qty : qty; // assume cups
            long gallons = (long) Math.ceil(cups / 16.0);
            if (gallons < 1) gallons = 1; // buy at least a gallon
            return "Milk: " + gallons + " " + pluralizeWord("gallon", gallons);
        }
        // Greek yogurt → 32 oz tubs (4 cups)
        if (item.contains("greek yogurt")) {
            double cups = unit.equals("cup") ? qty : qty;
            long tubs = (long) Math.ceil(cups / 4.0);
            return "Greek yogurt: " + tubs + " x 32 oz tub" + (tubs > 1 ? "s" : "");
        }
        // Rice/Quinoa/Oats → 2 lb bags (approx conversion from cups)
        if (c.equals("Grains")) {
            if (item.contains("rice") || item.contains("quinoa") || item.contains("oat")) {
                double lbPerCup = item.contains("oat") ? 0.3 : 0.5; // approx
                double pounds = (unit.equals("cup") ? qty * lbPerCup : qty);
                long bags = (long) Math.ceil(pounds / 2.0);
                String label = capitalizeWords(itemName);
                return label + ": " + bags + " x 2 lb bag" + (bags > 1 ? "s" : "");
            }
        }
        // Oils/Butter → 16 oz bottles from tbsp (1 tbsp ≈ 0.5 oz)
        if (c.equals("Pantry") && (item.contains("oil") || item.contains("butter"))) {
            double oz = unit.equals("tbsp") ? qty * 0.5 : qty;
            long bottles = (long) Math.ceil(oz / 16.0);
            String label = capitalizeWords(itemName);
            return label + ": " + bottles + " x 16 oz bottle" + (bottles > 1 ? "s" : "");
        }
        // Berries → containers (~1 cup per container)
        if (item.contains("berry") || item.contains("berries")) {
            double cups = unit.equals("cup") ? qty : qty;
            long containers = (long) Math.ceil(cups / 1.0);
            return "Berries: " + containers + " container" + (containers > 1 ? "s" : "");
        }
        // Broccoli → heads (~2 cups per head)
        if (item.contains("broccoli")) {
            double cups = unit.equals("cup") ? qty : qty;
            long heads = (long) Math.ceil(cups / 2.0);
            return "Broccoli: " + heads + " head" + (heads > 1 ? "s" : "");
        }
        // Spinach → 8 oz bags (~1 cup ≈ 1 oz)
        if (item.contains("spinach")) {
            double cups = unit.equals("cup") ? qty : qty;
            long bags = (long) Math.ceil(cups / 8.0);
            return "Spinach: " + bags + " x 8 oz bag" + (bags > 1 ? "s" : "");
        }
        // Proteins → show pounds rounded up to 0.5 lb
        if (c.equals("Proteins")) {
            if (!item.contains("egg")) {
                double pounds = unit.equals("oz") ? (qty / 16.0) : qty;
                double rounded = Math.ceil(pounds * 2.0) / 2.0; // 0.5 lb increments
                String label = capitalizeWords(itemName);
                return label + ": " + formatQty(rounded) + " lb";
            }
        }
        // Cheese → 8 oz blocks (if given in cups; 1 cup ≈ 4 oz)
        if (item.contains("cheese")) {
            double oz = unit.equals("cup") ? (qty * 4.0) : qty;
            long blocks = (long) Math.ceil(oz / 8.0);
            return "Cheese: " + blocks + " x 8 oz block" + (blocks > 1 ? "s" : "");
        }
        // Default: keep unit and qty (rename 'count' -> 'each' for readability)
        String displayUnit = unit.equals("count") ? "each" : unit;
        String qtyStr = displayUnit.isBlank() ? "" : (formatQty(qty) + " " + displayUnit);
        return capitalizeWords(itemName) + (qtyStr.isBlank() ? "" : ": " + qtyStr);
    }

    private String pluralize(String base, long count) {
        return count == 1 ? base : base + "s";
    }

    private String pluralizeWord(String word, long count) {
        return count == 1 ? word : word + "s";
    }

    // Canonicalize item names to reduce duplicates (e.g., "Grilled chicken breast" -> "Chicken")
    private String canonicalizeItem(String item) {
        String s = item == null ? "" : item.toLowerCase(Locale.US).trim();
        // Remove parentheticals and common prep adjectives/sizes
        s = s.replaceAll("\\(.*?\\)", "");
        s = s.replaceAll("\\b(grilled|baked|roasted|steamed|boiled|cooked|plain|fresh)\\b", "");
        s = s.replaceAll("\\b(large|medium|small)\\b", "");
        s = s.replaceAll("\\s+", " ").trim();

        // Collapse common variants to a base ingredient
        Map<String,String> map = new LinkedHashMap<>();
        map.put("chicken breast", "chicken");
        map.put("salmon fillet", "salmon");
        map.put("ground beef 90/10", "ground beef");
        map.put("ground beef", "ground beef");
        map.put("ground turkey 93/7", "ground turkey");
        map.put("whole milk", "milk");
        map.put("brown rice", "rice");
        map.put("white rice", "rice");
        map.put("broccoli florets", "broccoli");
        map.put("spinach leaves", "spinach");
        map.put("bell peppers", "bell pepper");
        map.put("sweet potatoes", "sweet potato");
        map.put("eggs", "egg");
        map.put("greek yogurt", "greek yogurt");
        map.put("mixed berries", "berries");
        map.put("strawberries", "berries");
        map.put("blueberries", "berries");
        map.put("raspberries", "berries");
        // Common misspellings
        map.put("aspargus", "asparagus");
        map.put("berrys", "berries");

        for (Map.Entry<String,String> e : map.entrySet()) {
            if (s.contains(e.getKey())) {
                s = e.getValue();
                break;
            }
        }

        // Simple, safer singularization: avoid breaking words like 'asparagus' and 'berries'
        if (s.length() > 3) {
            if (s.endsWith("ies")) {
                // keep 'berries' as plural canonical; do not convert to 'berry'
            } else if (s.endsWith("us")) {
                // avoid trimming latin '-us' words like 'asparagus'
            } else if (s.endsWith("s") && !s.endsWith("ss")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return capitalizeWords(s);
    }

    // Normalize units and set sensible defaults by category
    private String normalizeUnit(String unit, String item, String category) {
        String u = unit == null ? "" : unit.toLowerCase(Locale.US).trim();
        if (u.contains(" ")) {
            u = u.split(" ")[0];
        }
        if (u.matches("^(cups?|c)$")) u = "cup";
        else if (u.matches("^(tablespoons?|tbsp|tbs|tbl)$")) u = "tbsp";
        else if (u.matches("^(teaspoons?|tsp)$")) u = "tsp";
        else if (u.matches("^(ounces?|oz)$")) u = "oz";
        else if (u.matches("^(pounds?|lb|lbs)$")) u = "lb";
        else if (u.contains("egg") || u.contains("banana") || u.contains("potato") || u.equals("medium") || u.equals("large") || u.equals("small") || u.equals("count")) {
            u = "count";
        }
        if (u.isBlank()) {
            if ("Proteins".equals(category)) u = "oz";
            else if ("Dairy".equals(category)) u = "cup";
            else if ("Produce".equals(category)) u = "count";
        }
        return u;
    }

    private Map<String, Map<String, Double>> reduceCategory(String category, Map<String, Map<String, Double>> itemUnits) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> e : itemUnits.entrySet()) {
            String item = e.getKey();
            String target = targetUnitFor(category, item);
            double total = 0.0;
            for (Map.Entry<String, Double> u : e.getValue().entrySet()) {
                total += convertUnit(u.getValue(), u.getKey(), target, category, item);
            }
            Map<String, Double> m = new LinkedHashMap<>();
            if (total > 0) m.put(target, total);
            out.put(item, m);
        }
        return out;
    }

    private String targetUnitFor(String category, String item) {
        String s = item.toLowerCase(Locale.US);
        if ("Proteins".equals(category)) {
            if (s.contains("egg")) return "count";
            return "oz";
        }
        if ("Dairy".equals(category)) return "cup";
        if ("Grains".equals(category)) return "cup";
        if ("Produce".equals(category)) return "count";
        if ("Pantry".equals(category)) {
            if (s.contains("oil") || s.contains("butter")) return "tbsp";
        }
        return "";
    }

    private double convertUnit(double qty, String from, String to, String category, String item) {
        String f = (from == null ? "" : from);
        if (to == null || to.isBlank() || f.equals(to)) return qty;
        // weight conversions
        if (f.equals("lb") && to.equals("oz")) return qty * 16.0;
        if (f.equals("oz") && to.equals("lb")) return qty / 16.0;
        // volume conversions
        if (f.equals("tbsp") && to.equals("cup")) return qty / 16.0;
        if (f.equals("tsp") && to.equals("tbsp")) return qty / 3.0;
        if (f.equals("tsp") && to.equals("cup")) return qty / 48.0; // 3 tsp = 1 tbsp; 16 tbsp = 1 cup
        if (f.equals("cup") && to.equals("tbsp")) return qty * 16.0;
        if (f.equals("tbsp") && to.equals("tsp")) return qty * 3.0;
        // count stays count
        if (f.equals("count") && to.equals("count")) return qty;
        // if unknown conversion, keep original quantity (best-effort)
        return qty;
    }

    private String capitalizeWords(String s) {
        if (s == null || s.isBlank()) return s;
        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1));
            out.append(' ');
        }
        return out.toString().trim();
    }

    // --- DTOs for parsing ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MealPlanDTO {
        public List<DayDTO> days;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DayDTO {
        @JsonProperty("day") public int day;
        @JsonProperty("meals") public List<MealDTO> meals;
        @JsonProperty("dailyTotals") public MacroTargetsDTO dailyTotals;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MealDTO {
        public String name;
        public List<FoodItemDTO> foods;
        public MacroTargetsDTO macros;
        public RecipeDTO recipe;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FoodItemDTO {
        public String item;
        public String portion;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MacroTargetsDTO {
        public int calories;
        public int protein;
        public int carbs;
        public int fat;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RecipeDTO {
        public String name;
        public List<String> ingredients;
        public List<String> instructions;
        public String prepTime;
        public String cookTime;
        public String totalTime;
    }

}
