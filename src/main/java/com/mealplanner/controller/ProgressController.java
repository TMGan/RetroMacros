package com.mealplanner.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.User;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.FoodLogService;
import com.mealplanner.service.ProgressService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/progress")
public class ProgressController {

    // Clients can catch up on a missed day, but not rewrite old history or log the future.
    static final int MAX_BACKDATE_DAYS = 14;

    private final ProgressService progressService;
    private final CurrentUserService currentUserService;
    private final FoodLogService foodLogService;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProgressController(ProgressService progressService, CurrentUserService currentUserService,
                              FoodLogService foodLogService, UserRepository userRepository) {
        this.progressService = progressService;
        this.currentUserService = currentUserService;
        this.foodLogService = foodLogService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String progress(@AuthenticationPrincipal OAuth2User principal, HttpSession session, Model model) {
        User user = currentUserService.requireForView(principal, session);
        LocalDate today = foodLogService.today(user);
        model.addAttribute("user", user);
        model.addAttribute("summary", progressService.summary(user));
        model.addAttribute("chartJson", chartJson(progressService.chartData(user), mapper));
        model.addAttribute("today", today);
        model.addAttribute("earliestDate", today.minusDays(MAX_BACKDATE_DAYS));
        model.addAttribute("todayEntry", progressService.checkInOn(user, today).orElse(null));
        model.addAttribute("impersonating", currentUserService.isViewingAs(session));
        return "progress";
    }

    @PostMapping("/check-in")
    public String checkIn(@AuthenticationPrincipal OAuth2User principal,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam Double weight,
                          @RequestParam(required = false) Double bodyFat,
                          @RequestParam(required = false) Double waist,
                          @RequestParam(required = false) Double hips,
                          @RequestParam(required = false) Double chest,
                          @RequestParam(required = false) Double arm,
                          @RequestParam(required = false) Double thigh,
                          @RequestParam(required = false) Integer energy,
                          @RequestParam(required = false) Integer sleep,
                          @RequestParam(required = false) Integer hunger,
                          @RequestParam(required = false) Integer stress,
                          @RequestParam(required = false) String note,
                          RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);
        LocalDate today = foodLogService.today(user);
        LocalDate day = date == null ? today : date;

        String error = null;
        if (day.isAfter(today) || day.isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
            error = "Check-ins can be for today or up to " + MAX_BACKDATE_DAYS + " days back.";
        } else if (!inRange(weight, 70, 700)) {
            error = "Weight must be between 70 and 700 lb.";
        } else if (!optionalInRange(bodyFat, 3, 70)) {
            error = "Body fat must be between 3% and 70%.";
        } else if (!optionalInRange(waist, 10, 90) || !optionalInRange(hips, 10, 90) || !optionalInRange(chest, 10, 90)
                || !optionalInRange(arm, 4, 40) || !optionalInRange(thigh, 6, 60)) {
            error = "One of the measurements looks off. Use inches.";
        } else if (!optionalScore(energy) || !optionalScore(sleep) || !optionalScore(hunger) || !optionalScore(stress)) {
            error = "Scores must be between 1 and 5.";
        }
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/progress";
        }

        BodyCheckIn values = new BodyCheckIn();
        values.setWeightLbs(round1(weight));
        values.setBodyFatPct(round1(bodyFat));
        values.setWaistIn(round1(waist));
        values.setHipsIn(round1(hips));
        values.setChestIn(round1(chest));
        values.setArmIn(round1(arm));
        values.setThighIn(round1(thigh));
        values.setEnergy(energy);
        values.setSleep(sleep);
        values.setHunger(hunger);
        values.setStress(stress);
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 500) cleanNote = cleanNote.substring(0, 500);
        values.setNote(cleanNote == null || cleanNote.isEmpty() ? null : cleanNote);
        progressService.saveCheckIn(user, day, values);

        redirectAttributes.addFlashAttribute("success", day.equals(today) ? "Check-in saved." : "Check-in saved for " + day + ".");
        return "redirect:/progress";
    }

    @PostMapping("/goal")
    public String setGoal(@AuthenticationPrincipal OAuth2User principal,
                          @RequestParam(required = false) Double goalWeight,
                          RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);
        if (goalWeight != null && !inRange(goalWeight, 70, 700)) {
            redirectAttributes.addFlashAttribute("error", "Goal weight must be between 70 and 700 lb.");
            return "redirect:/progress";
        }
        user.setGoalWeightLbs(round1(goalWeight));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", goalWeight == null ? "Goal weight cleared." : "Goal weight saved.");
        return "redirect:/progress";
    }

    /** Chart data as JSON for a script tag. Escapes "<" so it can never close the tag early. */
    static String chartJson(Map<String, Object> data, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(data).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static boolean inRange(Double value, double min, double max) {
        return value != null && !value.isNaN() && value >= min && value <= max;
    }

    private static boolean optionalInRange(Double value, double min, double max) {
        return value == null || inRange(value, min, max);
    }

    private static boolean optionalScore(Integer score) {
        return score == null || (score >= 1 && score <= 5);
    }

    private static Double round1(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
