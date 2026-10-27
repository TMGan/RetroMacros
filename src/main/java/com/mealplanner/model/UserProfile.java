package com.mealplanner.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** What the client entered on the plan form, used for the targets and the AI prompt. */
public class UserProfile {

    public static final String STYLE_BALANCED = "balanced";
    public static final String STYLE_HIGH_PROTEIN = "higher-protein";
    public static final String STYLE_HIGH_CARB = "higher-carb";
    public static final String STYLE_LOW_CARB = "lower-carb";
    public static final String STYLE_CUSTOM = "custom";
    public static final List<String> MACRO_STYLES = List.of(STYLE_BALANCED, STYLE_HIGH_PROTEIN, STYLE_HIGH_CARB, STYLE_LOW_CARB, STYLE_CUSTOM);

    public static final String EFFORT_SIMPLE = "simple";
    public static final String EFFORT_STANDARD = "standard";
    public static final String EFFORT_COOK = "cook";
    public static final List<String> EFFORT_LEVELS = List.of(EFFORT_SIMPLE, EFFORT_STANDARD, EFFORT_COOK);

    private double weight; // lbs
    private int heightFeet;
    private int heightInches;
    private int age;
    private String sex; // "Male" or "Female"
    private String activityLevel; // e.g., Sedentary, Lightly Active, etc.
    private String fitnessGoal; // Lose Weight, Maintain Weight, Build Muscle
    private List<String> allergies;

    private String bmrFormula = "mifflin-st-jeor";
    private Double bodyFatPercentage;
    private Double goalWeight;
    private String macroStyle = STYLE_BALANCED;
    // Only used when macroStyle is custom.
    private int customProteinPercent = 30;
    private int customCarbsPercent = 40;
    private int customFatPercent = 30;
    private String effortLevel = EFFORT_STANDARD;
    private Set<HealthCondition> healthConditions = EnumSet.noneOf(HealthCondition.class);
    private String healthNotes;
    // True once the coach has cleared a flagged client for normal targets.
    private boolean healthCleared;

    public UserProfile() {
        this.allergies = new ArrayList<>();
    }

    public UserProfile(double weight, int heightFeet, int heightInches, int age,
                       String sex, String activityLevel, String fitnessGoal, List<String> allergies) {
        this.weight = weight;
        this.heightFeet = heightFeet;
        this.heightInches = heightInches;
        this.age = age;
        this.sex = sex;
        this.activityLevel = activityLevel;
        this.fitnessGoal = fitnessGoal;
        this.allergies = allergies != null ? allergies : new ArrayList<>();
    }

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
    public List<String> getAllergies() { return allergies; }
    public void setAllergies(List<String> allergies) { this.allergies = allergies; }

    public String getBmrFormula() { return bmrFormula; }
    public void setBmrFormula(String bmrFormula) { this.bmrFormula = bmrFormula; }
    public Double getBodyFatPercentage() { return bodyFatPercentage; }
    public void setBodyFatPercentage(Double bodyFatPercentage) { this.bodyFatPercentage = bodyFatPercentage; }
    public Double getGoalWeight() { return goalWeight; }
    public void setGoalWeight(Double goalWeight) { this.goalWeight = goalWeight; }
    public String getMacroStyle() { return macroStyle; }
    public void setMacroStyle(String macroStyle) { this.macroStyle = macroStyle; }
    public int getCustomProteinPercent() { return customProteinPercent; }
    public void setCustomProteinPercent(int customProteinPercent) { this.customProteinPercent = customProteinPercent; }
    public int getCustomCarbsPercent() { return customCarbsPercent; }
    public void setCustomCarbsPercent(int customCarbsPercent) { this.customCarbsPercent = customCarbsPercent; }
    public int getCustomFatPercent() { return customFatPercent; }
    public void setCustomFatPercent(int customFatPercent) { this.customFatPercent = customFatPercent; }
    public String getEffortLevel() { return effortLevel; }
    public void setEffortLevel(String effortLevel) { this.effortLevel = effortLevel; }
    public Set<HealthCondition> getHealthConditions() { return healthConditions; }
    public void setHealthConditions(Set<HealthCondition> healthConditions) {
        this.healthConditions = healthConditions == null ? EnumSet.noneOf(HealthCondition.class) : healthConditions;
    }
    public String getHealthNotes() { return healthNotes; }
    public void setHealthNotes(String healthNotes) { this.healthNotes = healthNotes; }
    public boolean isHealthCleared() { return healthCleared; }
    public void setHealthCleared(boolean healthCleared) { this.healthCleared = healthCleared; }

    // The client's "foods to avoid". Checked in code like allergies, but a miss isn't dangerous.
    private java.util.List<String> avoidedFoods = java.util.List.of();
    public java.util.List<String> getAvoidedFoods() { return avoidedFoods; }
    public void setAvoidedFoods(java.util.List<String> avoidedFoods) { this.avoidedFoods = avoidedFoods == null ? java.util.List.of() : avoidedFoods; }

    // Daily calories the coach set for this client, or null to use the formula.
    private Integer calorieOverride;
    public Integer getCalorieOverride() { return calorieOverride; }
    public void setCalorieOverride(Integer calorieOverride) { this.calorieOverride = calorieOverride; }

    // Weekly change from weigh-ins (see WeeklyAdjustmentService), in calories a day.
    private int calorieAdjustment;
    public int getCalorieAdjustment() { return calorieAdjustment; }
    public void setCalorieAdjustment(int calorieAdjustment) { this.calorieAdjustment = calorieAdjustment; }
}
