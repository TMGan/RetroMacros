package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    private String profilePictureUrl;

    @Column(unique = true, nullable = false)
    private String googleId;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @Column(name = "is_admin")
    private boolean isAdmin = false;

    // Optional timezone preference for admin/user pages
    private String timezone;

    // Tracks activity used in admin views
    private LocalDateTime lastActiveAt;

    // Target weight for the progress page. The client sets it; the coach can change it.
    @Column(name = "goal_weight_lbs")
    private Double goalWeightLbs;

    // Which figure the body-measurement diagram shows: "male" or "female". Chosen by the client.
    @Column(name = "body_diagram", length = 10)
    private String bodyDiagram;

    // Health-screen answers (HealthCondition keys, comma separated) and free-text notes.
    @Column(name = "health_conditions", length = 200)
    private String healthConditions;
    @Column(name = "health_notes", length = 300)
    private String healthNotes;
    // Set by the coach once a doctor has cleared a flagged client for normal targets.
    @Column(name = "health_cleared")
    private Boolean healthCleared;

    // When the client agreed to the disclaimer. Null until they do.
    @Column(name = "disclaimer_accepted_at")
    private LocalDateTime disclaimerAcceptedAt;

    // First day of the client's current 4-week ramp, or null when not ramping.
    @Column(name = "ramp_start_date")
    private java.time.LocalDate rampStartDate;

    // Last day of full access, when the coach set one; otherwise the challenge default applies.
    @Column(name = "access_until")
    private java.time.LocalDate accessUntil;

    // Weekly calorie change from weigh-ins, added to the goal calories. Null means none.
    @Column(name = "calorie_adjustment")
    private Integer calorieAdjustment;

    // Monday of the last week the weigh-ins were checked, so it runs once a week.
    @Column(name = "adjustment_checked_week")
    private java.time.LocalDate adjustmentCheckedWeek;

    // Day the adjustment last changed.
    @Column(name = "calorie_adjusted_on")
    private java.time.LocalDate calorieAdjustedOn;

    public int getCalorieAdjustment() { return calorieAdjustment == null ? 0 : calorieAdjustment; }
    public void setCalorieAdjustment(Integer calorieAdjustment) { this.calorieAdjustment = calorieAdjustment; }
    public java.time.LocalDate getAdjustmentCheckedWeek() { return adjustmentCheckedWeek; }
    public void setAdjustmentCheckedWeek(java.time.LocalDate adjustmentCheckedWeek) { this.adjustmentCheckedWeek = adjustmentCheckedWeek; }
    public java.time.LocalDate getCalorieAdjustedOn() { return calorieAdjustedOn; }
    public void setCalorieAdjustedOn(java.time.LocalDate calorieAdjustedOn) { this.calorieAdjustedOn = calorieAdjustedOn; }

    public java.time.LocalDate getAccessUntil() { return accessUntil; }
    public void setAccessUntil(java.time.LocalDate accessUntil) { this.accessUntil = accessUntil; }

    public String getHealthConditions() { return healthConditions; }
    public void setHealthConditions(String healthConditions) { this.healthConditions = healthConditions; }
    public String getHealthNotes() { return healthNotes; }
    public void setHealthNotes(String healthNotes) { this.healthNotes = healthNotes; }
    public boolean isHealthCleared() { return Boolean.TRUE.equals(healthCleared); }
    public void setHealthCleared(Boolean healthCleared) { this.healthCleared = healthCleared; }
    public boolean hasHealthFlags() { return healthConditions != null && !healthConditions.isBlank(); }
    /** Health answers that hold plans at maintenance until the coach clears them. */
    public boolean hasReviewFlags() { return HealthCondition.needsReview(HealthCondition.parse(healthConditions)); }
    public boolean isOnGlp1() { return HealthCondition.parse(healthConditions).contains(HealthCondition.GLP1); }
    public LocalDateTime getDisclaimerAcceptedAt() { return disclaimerAcceptedAt; }
    public void setDisclaimerAcceptedAt(LocalDateTime disclaimerAcceptedAt) { this.disclaimerAcceptedAt = disclaimerAcceptedAt; }
    public java.time.LocalDate getRampStartDate() { return rampStartDate; }
    public void setRampStartDate(java.time.LocalDate rampStartDate) { this.rampStartDate = rampStartDate; }

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavedMealPlan> savedMealPlans = new ArrayList<>();

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    public User(String email, String name, String googleId) {
        this();
        this.email = email;
        this.name = name;
        this.googleId = googleId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public List<SavedMealPlan> getSavedMealPlans() { return savedMealPlans; }
    public void setSavedMealPlans(List<SavedMealPlan> savedMealPlans) { this.savedMealPlans = savedMealPlans; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public Double getGoalWeightLbs() { return goalWeightLbs; }
    public void setGoalWeightLbs(Double goalWeightLbs) { this.goalWeightLbs = goalWeightLbs; }

    public String getBodyDiagram() { return bodyDiagram; }
    public void setBodyDiagram(String bodyDiagram) { this.bodyDiagram = bodyDiagram; }
}
