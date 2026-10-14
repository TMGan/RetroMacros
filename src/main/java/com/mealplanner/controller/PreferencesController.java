package com.mealplanner.controller;

import com.mealplanner.model.User;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.UserFoodPreferencesService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/preferences")
public class PreferencesController {

    // These fields are pasted into the AI prompt, so they are length-capped.
    private static final int MAX_FOOD_LIST_LENGTH = 500;
    private static final int MAX_CHOICE_LENGTH = 50;

    private final UserFoodPreferencesService preferencesService;
    private final CurrentUserService currentUserService;

    public PreferencesController(UserFoodPreferencesService preferencesService,
                                 CurrentUserService currentUserService) {
        this.preferencesService = preferencesService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/save")
    public String savePreferences(@AuthenticationPrincipal OAuth2User principal,
                                  @RequestParam String preferredFoods,
                                  @RequestParam(required = false) String avoidedFoods,
                                  @RequestParam String cookingPreference,
                                  @RequestParam String dietaryStyle,
                                  RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);

        preferencesService.savePreferences(user,
                limit(preferredFoods, MAX_FOOD_LIST_LENGTH),
                limit(avoidedFoods, MAX_FOOD_LIST_LENGTH),
                limit(cookingPreference, MAX_CHOICE_LENGTH),
                limit(dietaryStyle, MAX_CHOICE_LENGTH));
        redirectAttributes.addFlashAttribute("success", "Food preferences saved!");
        return "redirect:/dashboard";
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
