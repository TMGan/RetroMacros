package com.mealplanner.controller;

import com.mealplanner.model.FoodLog;
import com.mealplanner.model.User;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.AiQuotaService;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.FoodLogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/food-log")
public class FoodLogController {

    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_MEAL_NAME_LENGTH = 100;

    private final FoodLogService foodLogService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AiQuotaService aiQuotaService;

    public FoodLogController(FoodLogService foodLogService,
                             UserRepository userRepository,
                             CurrentUserService currentUserService,
                             AiQuotaService aiQuotaService) {
        this.foodLogService = foodLogService;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.aiQuotaService = aiQuotaService;
    }

    @PostMapping("/add")
    public String addFoodLog(@AuthenticationPrincipal OAuth2User principal,
                             @RequestParam String mealName,
                             @RequestParam String foodDescription,
                             @RequestParam int calories,
                             @RequestParam int protein,
                             @RequestParam int carbs,
                             @RequestParam int fat,
                             @RequestParam(required = false, name = "timezone") String timezone,
                             RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);
        String error = saveLog(user, mealName, foodDescription, calories, protein, carbs, fat, timezone, false);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        } else {
            redirectAttributes.addFlashAttribute("success", "Food logged successfully!");
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/quick-add")
    public String quickAddFromMealPlan(@AuthenticationPrincipal OAuth2User principal,
                                       @RequestParam String mealName,
                                       @RequestParam String foodDescription,
                                       @RequestParam int calories,
                                       @RequestParam int protein,
                                       @RequestParam int carbs,
                                       @RequestParam int fat,
                                       @RequestParam(required = false, name = "timezone") String timezone,
                                       RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);
        String error = saveLog(user, mealName, foodDescription, calories, protein, carbs, fat, timezone, true);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        } else {
            redirectAttributes.addFlashAttribute("success", "Meal logged successfully!");
        }
        return "redirect:/dashboard";
    }

    /** Validates and stores one entry. Returns an error message, or null on success. */
    private String saveLog(User user, String mealName, String foodDescription,
                           int calories, int protein, int carbs, int fat,
                           String timezone, boolean fromMealPlan) {
        String description = foodDescription == null ? "" : foodDescription.trim();
        String meal = mealName == null ? "" : mealName.trim();
        if (description.isEmpty() || description.length() > MAX_DESCRIPTION_LENGTH) {
            return "Food description must be between 1 and " + MAX_DESCRIPTION_LENGTH + " characters.";
        }
        if (meal.length() > MAX_MEAL_NAME_LENGTH) {
            return "Meal name is too long.";
        }
        if (calories < 0 || calories > 5000 || protein < 0 || protein > 500
                || carbs < 0 || carbs > 1000 || fat < 0 || fat > 500) {
            return "Those macro values are out of range.";
        }

        // Remember the browser's timezone so "today" means the client's day, not the server's.
        if (timezone != null && !timezone.isBlank() && !timezone.equals(user.getTimezone())) {
            try {
                ZoneId.of(timezone);
                user.setTimezone(timezone);
                userRepository.save(user);
            } catch (Exception ignore) {}
        }
        ZoneId zone = foodLogService.zoneFor(user);

        FoodLog foodLog = new FoodLog();
        foodLog.setUser(user);
        foodLog.setMealName(meal);
        foodLog.setFoodDescription(description);
        foodLog.setCalories(calories);
        foodLog.setProtein(protein);
        foodLog.setCarbs(carbs);
        foodLog.setFat(fat);
        foodLog.setFromMealPlan(fromMealPlan);
        foodLog.setLogDate(LocalDate.now(zone));
        foodLog.setTimeLogged(LocalDateTime.now(zone));
        foodLogService.addFoodLog(foodLog);
        return null;
    }

    @PostMapping("/delete/{id}")
    public String deleteFoodLog(@AuthenticationPrincipal OAuth2User principal,
                                @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);
        FoodLog foodLog = foodLogService.getFoodLogById(id);
        // Same response for "missing" and "not yours" so IDs cannot be probed.
        if (foodLog == null || !foodLog.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        foodLogService.deleteFoodLog(id);
        redirectAttributes.addFlashAttribute("success", "Food log deleted!");
        return "redirect:/dashboard";
    }

    @PostMapping("/random-suggestion")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getRandomSuggestion(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(required = false) Integer targetCalories) {
        User user = currentUserService.require(principal);
        Map<String, Object> response = new HashMap<>();
        try {
            boolean offline = currentUserService.isDemo(user);
            if (!offline) aiQuotaService.consume(user, AiQuotaService.Kind.ESTIMATE);
            if (targetCalories == null) targetCalories = 500;
            targetCalories = Math.max(100, Math.min(2000, targetCalories));
            Map<String, Object> suggestion = foodLogService.generateRandomHealthyMeal(user, targetCalories, offline);
            if (suggestion == null) {
                response.put("success", false);
                response.put("error", "Could not generate suggestion.");
                return ResponseEntity.ok(response);
            }
            response.put("success", true);
            response.put("foodDescription", suggestion.get("description"));
            response.put("ingredients", suggestion.get("ingredients"));
            response.put("steps", suggestion.get("steps"));
            // What gets saved if they log it: the dish name plus what went into it.
            String logDescription = suggestion.get("description") + " (" + String.join(", ", (List<String>) suggestion.get("ingredients")) + ")";
            if (logDescription.length() > MAX_DESCRIPTION_LENGTH) logDescription = logDescription.substring(0, MAX_DESCRIPTION_LENGTH);
            response.put("logDescription", logDescription);
            response.put("calories", suggestion.get("calories"));
            response.put("protein", suggestion.get("protein"));
            response.put("carbs", suggestion.get("carbs"));
            response.put("fat", suggestion.get("fat"));
            return ResponseEntity.ok(response);
        } catch (AiQuotaService.QuotaExceededException e) {
            return quotaResponse(e);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Error generating suggestion.");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/estimate-with-alternative")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> estimateWithAlternative(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam String foodDescription) {
        User user = currentUserService.require(principal);
        Map<String, Object> response = new HashMap<>();
        try {
            boolean offline = currentUserService.isDemo(user);
            if (!offline) aiQuotaService.consume(user, AiQuotaService.Kind.ESTIMATE);
            Map<String, Integer> macros = foodLogService.estimateMacrosWithAI(user, foodDescription, offline);
            if (macros == null) {
                response.put("success", false);
                response.put("error", "Please enter a valid food description.");
                return ResponseEntity.ok(response);
            }
            response.put("success", true);
            response.put("calories", macros.get("calories"));
            response.put("protein", macros.get("protein"));
            response.put("carbs", macros.get("carbs"));
            response.put("fat", macros.get("fat"));
            String healthier = offline ? null : foodLogService.suggestHealthierAlternative(
                    user,
                    foodDescription,
                    macros.get("calories"),
                    macros.get("protein"),
                    macros.get("carbs"),
                    macros.get("fat")
            );
            if (healthier != null && !healthier.isBlank()) {
                response.put("healthierAlternative", healthier);
            }
            return ResponseEntity.ok(response);
        } catch (AiQuotaService.QuotaExceededException e) {
            return quotaResponse(e);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Could not estimate macros.");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/history")
    public String viewHistory(@AuthenticationPrincipal OAuth2User principal, HttpSession session, Model model) {
        User user = currentUserService.requireForView(principal, session);
        List<FoodLog> last7Days = foodLogService.getLast7DaysFoodLogs(user);
        Map<LocalDate, Map<String, Integer>> dailyTotalsMap = new HashMap<>();
        LocalDate today = foodLogService.today(user);
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.minusDays(i);
            dailyTotalsMap.put(date, foodLogService.calculateDailyTotals(user, date));
        }
        model.addAttribute("user", user);
        model.addAttribute("foodLogs", last7Days);
        model.addAttribute("dailyTotalsMap", dailyTotalsMap);
        return "food-log-history";
    }

    @PostMapping("/estimate-macros")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> estimateMacros(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam String foodDescription) {
        User user = currentUserService.require(principal);
        Map<String, Object> response = new HashMap<>();
        try {
            boolean offline = currentUserService.isDemo(user);
            if (!offline) aiQuotaService.consume(user, AiQuotaService.Kind.ESTIMATE);
            Map<String, Integer> macros = foodLogService.estimateMacrosWithAI(user, foodDescription, offline);
            if (macros == null) {
                response.put("success", false);
                response.put("error", "Please enter a valid food description.");
                return ResponseEntity.ok(response);
            }
            response.put("success", true);
            response.put("calories", macros.get("calories"));
            response.put("protein", macros.get("protein"));
            response.put("carbs", macros.get("carbs"));
            response.put("fat", macros.get("fat"));
            return ResponseEntity.ok(response);
        } catch (AiQuotaService.QuotaExceededException e) {
            return quotaResponse(e);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Couldn't estimate macros. Please enter them manually.");
            return ResponseEntity.status(500).body(response);
        }
    }

    private ResponseEntity<Map<String, Object>> quotaResponse(AiQuotaService.QuotaExceededException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        return ResponseEntity.ok(response);
    }
}
