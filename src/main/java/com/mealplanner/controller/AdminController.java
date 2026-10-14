package com.mealplanner.controller;

import com.mealplanner.model.FoodLog;
import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.model.AdminAuditLog;
import com.mealplanner.model.AllowedEmail;
import com.mealplanner.repository.AdminAuditLogRepository;
import com.mealplanner.repository.AllowedEmailRepository;
import com.mealplanner.repository.FoodLogRepository;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final SavedMealPlanRepository savedMealPlanRepository;
    private final FoodLogRepository foodLogRepository;
    private final AllowedEmailRepository allowedEmailRepository;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminController(AdminService adminService,
                           UserRepository userRepository,
                           SavedMealPlanRepository savedMealPlanRepository,
                           FoodLogRepository foodLogRepository,
                           AllowedEmailRepository allowedEmailRepository,
                           AdminAuditLogRepository auditLogRepository) {
        this.allowedEmailRepository = allowedEmailRepository;
        this.auditLogRepository = auditLogRepository;
        this.adminService = adminService;
        this.userRepository = userRepository;
        this.savedMealPlanRepository = savedMealPlanRepository;
        this.foodLogRepository = foodLogRepository;
    }

    private User getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return null;
        String email = principal.getAttribute("email");
        String googleId = principal.getAttribute("sub");
        User user = null;
        if (email != null) user = userRepository.findByEmail(email).orElse(null);
        if (user == null && googleId != null) user = userRepository.findByGoogleId(googleId).orElse(null);
        return user;
    }

    private void requireAdmin(@AuthenticationPrincipal OAuth2User principal) {
        User current = getCurrentUser(principal);
        if (current == null || !current.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    @GetMapping("")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        requireAdmin(principal);
        Map<String, Object> stats = adminService.getDashboardStats();
        model.addAllAttributes(stats);
        List<User> recent = userRepository.findTop10ByOrderByCreatedAtDesc();
        model.addAttribute("recentUsers", recent);
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String allUsers(@AuthenticationPrincipal OAuth2User principal,
                           @RequestParam(required = false) String search,
                           @RequestParam(required = false) String sortBy,
                           Model model) {
        requireAdmin(principal);

        List<Map<String, Object>> users;
        if (search != null && !search.isEmpty()) {
            List<User> results = adminService.searchUsers(search);
            users = new ArrayList<>();
            for (User u : results) {
                Map<String, Object> m = new HashMap<>();
                m.put("user", u);
                m.put("totalFoodLogs", foodLogRepository.countByUser(u));
                m.put("totalMealPlans", savedMealPlanRepository.countByUser(u));
                FoodLog last = foodLogRepository.findTopByUserOrderByLogDateDesc(u);
                m.put("lastActive", last != null ? last.getLogDate() : null);
                users.add(m);
            }
        } else {
            users = adminService.getAllUsersWithStats();
        }

        if (sortBy != null) {
            switch (sortBy) {
                case "name" -> users.sort(Comparator.comparing(u -> ((User) u.get("user")).getName(), Comparator.nullsLast(String::compareToIgnoreCase)));
                case "email" -> users.sort(Comparator.comparing(u -> ((User) u.get("user")).getEmail(), Comparator.nullsLast(String::compareToIgnoreCase)));
                case "created" -> users.sort(Comparator.comparing(u -> ((User) u.get("user")).getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())));
                case "active" -> users.sort(Comparator.comparing(u -> (LocalDate) u.get("lastActive"), Comparator.nullsLast(Comparator.reverseOrder())));
            }
        }

        model.addAttribute("users", users);
        model.addAttribute("search", search);
        model.addAttribute("sortBy", sortBy);
        return "admin/users";
    }

    @GetMapping("/users/{userId}")
    public String userDetails(@AuthenticationPrincipal OAuth2User principal,
                              @PathVariable Long userId,
                              Model model) {
        requireAdmin(principal);
        Map<String, Object> details = adminService.getUserDetails(userId);
        model.addAllAttributes(details);
        return "admin/user-details";
    }

    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@AuthenticationPrincipal OAuth2User principal,
                             @PathVariable Long userId) {
        requireAdmin(principal);
        User admin = getCurrentUser(principal);
        if (admin.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account here");
        }
        adminService.deleteUser(userId);
        auditLogRepository.save(new AdminAuditLog(admin.getEmail(), "DELETE_USER", "user:" + userId));
        return "redirect:/admin/users?deleted=true";
    }

    @PostMapping("/users/{userId}/impersonate")
    public String impersonateUser(@AuthenticationPrincipal OAuth2User principal,
                                  @PathVariable Long userId,
                                  HttpSession session) {
        requireAdmin(principal);
        User target = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User admin = getCurrentUser(principal);
        session.setAttribute("originalAdminId", admin != null ? admin.getId() : null);
        session.setAttribute("impersonating", true);
        session.setAttribute("impersonateUserId", target.getId());
        // Viewing a client account is read-only (ReadOnlyViewInterceptor) and always recorded.
        auditLogRepository.save(new AdminAuditLog(admin.getEmail(), "VIEW_AS", "user:" + target.getId()));
        return "redirect:/dashboard";
    }

    @GetMapping("/exit-impersonation")
    public String exitImpersonation(HttpSession session) {
        if (Boolean.TRUE.equals(session.getAttribute("impersonating"))) {
            session.removeAttribute("impersonating");
            session.removeAttribute("impersonateUserId");
            session.removeAttribute("originalAdminId");
        }
        return "redirect:/admin";
    }

    @GetMapping("/invites")
    public String invites(@AuthenticationPrincipal OAuth2User principal, Model model) {
        requireAdmin(principal);
        model.addAttribute("invites", allowedEmailRepository.findAllByOrderByInvitedAtDesc());
        return "admin/invites";
    }

    @PostMapping("/invites")
    public String addInvite(@AuthenticationPrincipal OAuth2User principal,
                            @RequestParam String email,
                            @RequestParam(required = false) String note) {
        requireAdmin(principal);
        String cleaned = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (cleaned.length() > 254 || !cleaned.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "redirect:/admin/invites?invalid=true";
        }
        if (!allowedEmailRepository.existsByEmailIgnoreCase(cleaned)) {
            String cleanedNote = note == null ? null : note.trim();
            if (cleanedNote != null && cleanedNote.length() > 200) cleanedNote = cleanedNote.substring(0, 200);
            allowedEmailRepository.save(new AllowedEmail(cleaned, cleanedNote));
            auditLogRepository.save(new AdminAuditLog(getCurrentUser(principal).getEmail(), "INVITE_ADD", cleaned));
        }
        return "redirect:/admin/invites?added=true";
    }

    @PostMapping("/invites/{inviteId}/delete")
    public String removeInvite(@AuthenticationPrincipal OAuth2User principal,
                               @PathVariable Long inviteId) {
        requireAdmin(principal);
        allowedEmailRepository.findById(inviteId).ifPresent(invite -> {
            allowedEmailRepository.delete(invite);
            auditLogRepository.save(new AdminAuditLog(getCurrentUser(principal).getEmail(), "INVITE_REMOVE", invite.getEmail()));
        });
        return "redirect:/admin/invites?removed=true";
    }

    @GetMapping("/meal-plans")
    public String allMealPlans(@AuthenticationPrincipal OAuth2User principal,
                               @RequestParam(required = false) Long userId,
                               Model model) {
        requireAdmin(principal);
        List<SavedMealPlan> plans;
        if (userId != null) {
            User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            plans = savedMealPlanRepository.findByUserOrderByCreatedAtDesc(user);
            model.addAttribute("filterUser", user);
        } else {
            plans = savedMealPlanRepository.findAllByOrderByCreatedAtDesc();
        }
        model.addAttribute("mealPlans", plans);
        return "admin/meal-plans";
    }

    @GetMapping("/meal-plans/{planId}")
    public String mealPlanDetails(@AuthenticationPrincipal OAuth2User principal,
                                  @PathVariable Long planId,
                                  Model model) {
        requireAdmin(principal);
        SavedMealPlan plan = savedMealPlanRepository.findById(planId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("plan", plan);
        model.addAttribute("user", plan.getUser());
        return "admin/meal-plan-details";
    }

    @GetMapping("/food-logs")
    public String allFoodLogs(@AuthenticationPrincipal OAuth2User principal,
                              @RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String date,
                              Model model) {
        requireAdmin(principal);
        List<FoodLog> foodLogs;
        if (userId != null) {
            User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            foodLogs = foodLogRepository.findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(user, LocalDate.of(1970,1,1), LocalDate.now());
            model.addAttribute("filterUser", user);
        } else {
            foodLogs = foodLogRepository.findAllByOrderByLogDateDesc();
        }
        if (date != null && !date.isEmpty()) {
            LocalDate filterDate = LocalDate.parse(date);
            foodLogs = foodLogs.stream().filter(f -> f.getLogDate().equals(filterDate)).collect(Collectors.toList());
            model.addAttribute("filterDate", date);
        }
        model.addAttribute("foodLogs", foodLogs);
        return "admin/food-logs";
    }

    @GetMapping("/ai-accuracy")
    public String aiAccuracy(@AuthenticationPrincipal OAuth2User principal, Model model) {
        requireAdmin(principal);
        Map<String, Object> metrics = adminService.getAIAccuracyMetrics();
        model.addAllAttributes(metrics);
        return "admin/ai-accuracy";
    }

    @GetMapping("/export/users")
    @ResponseBody
    public ResponseEntity<String> exportUsers(@AuthenticationPrincipal OAuth2User principal) {
        requireAdmin(principal);
        List<User> users = userRepository.findAll();
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Name,Email,Created At,Last Login,Is Admin\n");
        for (User user : users) {
            csv.append(user.getId()).append(",")
               .append(escapeCSV(user.getName())).append(",")
               .append(escapeCSV(user.getEmail())).append(",")
               .append(user.getCreatedAt()).append(",")
               .append(user.getLastLoginAt()).append(",")
               .append(user.isAdmin()).append("\n");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=users.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    @GetMapping("/export/meal-plans")
    @ResponseBody
    public ResponseEntity<String> exportMealPlans(@AuthenticationPrincipal OAuth2User principal) {
        requireAdmin(principal);
        List<SavedMealPlan> plans = savedMealPlanRepository.findAll();
        StringBuilder csv = new StringBuilder();
        csv.append("ID,User ID,User Email,Created At,Target Cal,Target P,Target C,Target F,Actual Cal,Actual P,Actual C,Actual F,Accuracy Score,Failed\n");
        for (SavedMealPlan plan : plans) {
            csv.append(plan.getId()).append(",")
               .append(plan.getUser().getId()).append(",")
               .append(escapeCSV(plan.getUser().getEmail())).append(",")
               .append(plan.getCreatedAt()).append(",")
               .append(plan.getTargetCalories()).append(",")
               .append(plan.getTargetProtein()).append(",")
               .append(plan.getTargetCarbs()).append(",")
               .append(plan.getTargetFat()).append(",")
               .append(plan.getActualCalories()).append(",")
               .append(plan.getActualProtein()).append(",")
               .append(plan.getActualCarbs()).append(",")
               .append(plan.getActualFat()).append(",")
               .append(plan.getAccuracyScore()).append(",")
               .append(plan.isGenerationFailed()).append("\n");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=meal-plans.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        // Stop spreadsheet apps from running a cell that starts like a formula.
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

