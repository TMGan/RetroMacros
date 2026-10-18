package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_meal_plans")
public class SavedMealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime createdAt;

    // snapshot of user profile at generation time
    private double weight;
    private int heightFeet;
    private int heightInches;
    private int age;
    private String sex;
    private String activityLevel;
    private String fitnessGoal;

    // macro targets
    private int targetCalories;
    private int targetProtein;
    private int targetCarbs;
    private int targetFat;

    @Column(columnDefinition = "TEXT")
    private String mealPlanJson;

    @Column(columnDefinition = "TEXT")
    private String groceryListJson;

    // Actual totals (optional, if computed post-generation)
    private Integer actualCalories;
    private Integer actualProtein;
    private Integer actualCarbs;
    private Integer actualFat;

    // Accuracy score 0-100 (nullable)
    private Double accuracyScore;

    // Generation status
    private boolean generationFailed = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // Allergies entered for this plan, comma separated. Swaps reuse them.
    @Column(columnDefinition = "TEXT")
    private String allergies;

    // Plan settings the client picked. Wrapper types so rows from before these columns load as null.
    @Column(length = 20)
    private String macroStyle;
    @Column(length = 20)
    private String effortLevel;
    // Ramp week this plan was built for (1 to 4), or null when not ramping.
    private Integer rampWeek;
    private Integer fiberTarget;
    private Integer waterOz;
    private Boolean healthHold;
    // Health-screen answers at the time, as HealthCondition keys.
    @Column(length = 200)
    private String healthConditions;
    // What the code checks still flagged after the retry, one per line. Shown to the coach.
    @Column(columnDefinition = "TEXT")
    private String qualityNotes;

    public String getMacroStyle() { return macroStyle; }
    public void setMacroStyle(String macroStyle) { this.macroStyle = macroStyle; }
    public String getEffortLevel() { return effortLevel; }
    public void setEffortLevel(String effortLevel) { this.effortLevel = effortLevel; }
    public Integer getRampWeek() { return rampWeek; }
    public void setRampWeek(Integer rampWeek) { this.rampWeek = rampWeek; }
    public Integer getFiberTarget() { return fiberTarget; }
    public void setFiberTarget(Integer fiberTarget) { this.fiberTarget = fiberTarget; }
    public Integer getWaterOz() { return waterOz; }
    public void setWaterOz(Integer waterOz) { this.waterOz = waterOz; }
    public Boolean getHealthHold() { return healthHold; }
    public void setHealthHold(Boolean healthHold) { this.healthHold = healthHold; }
    public String getHealthConditions() { return healthConditions; }
    public void setHealthConditions(String healthConditions) { this.healthConditions = healthConditions; }
    public String getQualityNotes() { return qualityNotes; }
    public void setQualityNotes(String qualityNotes) { this.qualityNotes = qualityNotes; }
    public boolean isSimple() { return "simple".equals(effortLevel); }

    public SavedMealPlan() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public int getHeightFeet() { return heightFeet; }
    public void setHeightFeet(int heightFeet) { this.heightFeet = heightFeet; }
    public int getHeightInches() { return heightInches; }
    public void setHeightInches(int heightInches) { this.heightInches = heightInches; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }
    public String getFitnessGoal() { return fitnessGoal; }
    public void setFitnessGoal(String fitnessGoal) { this.fitnessGoal = fitnessGoal; }
    public int getTargetCalories() { return targetCalories; }
    public void setTargetCalories(int targetCalories) { this.targetCalories = targetCalories; }
    public int getTargetProtein() { return targetProtein; }
    public void setTargetProtein(int targetProtein) { this.targetProtein = targetProtein; }
    public int getTargetCarbs() { return targetCarbs; }
    public void setTargetCarbs(int targetCarbs) { this.targetCarbs = targetCarbs; }
    public int getTargetFat() { return targetFat; }
    public void setTargetFat(int targetFat) { this.targetFat = targetFat; }
    public String getMealPlanJson() { return mealPlanJson; }
    public void setMealPlanJson(String mealPlanJson) { this.mealPlanJson = mealPlanJson; }
    public String getGroceryListJson() { return groceryListJson; }
    public void setGroceryListJson(String groceryListJson) { this.groceryListJson = groceryListJson; }

    public Integer getActualCalories() { return actualCalories; }
    public void setActualCalories(Integer actualCalories) { this.actualCalories = actualCalories; }
    public Integer getActualProtein() { return actualProtein; }
    public void setActualProtein(Integer actualProtein) { this.actualProtein = actualProtein; }
    public Integer getActualCarbs() { return actualCarbs; }
    public void setActualCarbs(Integer actualCarbs) { this.actualCarbs = actualCarbs; }
    public Integer getActualFat() { return actualFat; }
    public void setActualFat(Integer actualFat) { this.actualFat = actualFat; }
    public Double getAccuracyScore() { return accuracyScore; }
    public void setAccuracyScore(Double accuracyScore) { this.accuracyScore = accuracyScore; }
    public boolean isGenerationFailed() { return generationFailed; }
    public void setGenerationFailed(boolean generationFailed) { this.generationFailed = generationFailed; }
    public String getErrorMessage() { return errorMessage; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public java.util.List<String> getAllergyList() {
        if (allergies == null || allergies.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(allergies.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
