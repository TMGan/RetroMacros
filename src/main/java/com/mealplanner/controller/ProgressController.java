package com.mealplanner.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.User;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.CurrentUserService;
import com.mealplanner.service.FoodLogService;
import com.mealplanner.service.ProgressService;
import com.mealplanner.service.MeasurementService;
import com.mealplanner.model.BodyPart;
import java.util.EnumMap;
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
    // Measurements are monthly, so allow catching up on a session from the last month.
    static final int MAX_MEASUREMENT_BACKDATE_DAYS = 30;
    // The page's tabs, most used first: anchor id, label, icon. Goal weight lives in Overview.
    static final java.util.List<String[]> SECTIONS = java.util.List.of(
            new String[]{"checkin", "Check in", "bi-check2-circle"},
            new String[]{"overview", "Overview", "bi-speedometer2"},
            new String[]{"charts", "Charts", "bi-graph-up"});

    private final ProgressService progressService;
    private final MeasurementService measurementService;
    private final CurrentUserService currentUserService;
    private final FoodLogService foodLogService;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProgressController(ProgressService progressService, MeasurementService measurementService, CurrentUserService currentUserService,
                              FoodLogService foodLogService, UserRepository userRepository) {
        this.progressService = progressService;
        this.measurementService = measurementService;
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
        addMeasurementModel(model, user, measurementService);
        model.addAttribute("measurementEarliest", today.minusDays(MAX_MEASUREMENT_BACKDATE_DAYS));
        model.addAttribute("impersonating", currentUserService.isViewingAs(session));
        model.addAttribute("progressSections", SECTIONS);
        // The coach viewing a client is read-only, so no goal editing there.
        model.addAttribute("goalEditable", !currentUserService.isViewingAs(session));
        return "progress";
    }

    @PostMapping("/check-in")
    public String checkIn(@AuthenticationPrincipal OAuth2User principal,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam Double weight,
                          @RequestParam(required = false) Double bodyFat,
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
        } else if (!optionalScore(energy) || !optionalScore(sleep) || !optionalScore(hunger) || !optionalScore(stress)) {
            error = "Scores must be between 1 and 5.";
        }
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/progress#checkin";
        }

        BodyCheckIn values = new BodyCheckIn();
        values.setWeightLbs(round1(weight));
        values.setBodyFatPct(round1(bodyFat));
        values.setEnergy(energy);
        values.setSleep(sleep);
        values.setHunger(hunger);
        values.setStress(stress);
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 500) cleanNote = cleanNote.substring(0, 500);
        values.setNote(cleanNote == null || cleanNote.isEmpty() ? null : cleanNote);
        progressService.saveCheckIn(user, day, values);

        redirectAttributes.addFlashAttribute("success", day.equals(today) ? "Check-in saved." : "Check-in saved for " + day + ".");
        return "redirect:/progress#overview";
    }

    /**
     * Saves a tape-measure session from the body diagram. Inputs are named m_&lt;spot&gt;
     * (m_waist, m_armLeft, ...); blank ones are skipped.
     */
    @PostMapping("/measurements")
    public String saveMeasurements(@AuthenticationPrincipal OAuth2User principal,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) String diagram,
                                   @RequestParam Map<String, String> params,
                                   RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);
        LocalDate today = foodLogService.today(user);
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today) || day.isBefore(today.minusDays(MAX_MEASUREMENT_BACKDATE_DAYS))) {
            redirectAttributes.addFlashAttribute("measurementError", "Measurements can be for today or up to " + MAX_MEASUREMENT_BACKDATE_DAYS + " days back.");
            return "redirect:/progress#measurements";
        }

        Map<BodyPart, Double> values = new EnumMap<>(BodyPart.class);
        for (BodyPart part : BodyPart.values()) {
            String raw = params.get("m_" + part.getKey());
            if (raw == null || raw.isBlank()) continue;
            Double value;
            try {
                value = Double.valueOf(raw.trim());
            } catch (NumberFormatException e) {
                value = null;
            }
            if (value == null || !inRange(value, part.getMin(), part.getMax())) {
                redirectAttributes.addFlashAttribute("measurementError",
                        part.getLabel() + " should be between " + (int) part.getMin() + " and " + (int) part.getMax() + " inches.");
                return "redirect:/progress#measurements";
            }
            values.put(part, round1(value));
        }
        if (values.isEmpty()) {
            redirectAttributes.addFlashAttribute("measurementError", "Enter at least one measurement.");
            return "redirect:/progress#measurements";
        }

        if ("male".equals(diagram) || "female".equals(diagram)) {
            user.setBodyDiagram(diagram);
            userRepository.save(user);
        }
        measurementService.save(user, day, values);
        redirectAttributes.addFlashAttribute("measurementSuccess", "Measurements saved. Next reminder in 4 weeks.");
        return "redirect:/progress#measurements";
    }

    @PostMapping("/goal")
    public String setGoal(@AuthenticationPrincipal OAuth2User principal,
                          @RequestParam(required = false) Double goalWeight,
                          RedirectAttributes redirectAttributes) {
        User user = currentUserService.require(principal);
        if (goalWeight != null && !inRange(goalWeight, 70, 700)) {
            redirectAttributes.addFlashAttribute("error", "Goal weight must be between 70 and 700 lb.");
            return "redirect:/progress#overview";
        }
        user.setGoalWeightLbs(round1(goalWeight));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", goalWeight == null ? "Goal weight cleared." : "Goal weight saved.");
        return "redirect:/progress#overview";
    }

    /** Everything the measurement section needs. Shared with the coach's client page. */
    static void addMeasurementModel(Model model, User user, MeasurementService measurementService) {
        model.addAttribute("msummary", measurementService.summary(user));
        model.addAttribute("diagramRows", measurementService.diagram(user));
        model.addAttribute("bodyDiagram", "female".equals(user.getBodyDiagram()) ? "female" : "male");
        model.addAttribute("measurementJson", chartJson(measurementService.chartData(user), new ObjectMapper()));
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
