package com.mealplanner.service;

import com.mealplanner.model.HealthCondition;
import com.mealplanner.model.MacroTargets;
import com.mealplanner.model.NutritionTargets;
import com.mealplanner.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class MacroCalculatorService {

    public double poundsToKg(double lbs) {
        return lbs * 0.45359237d;
    }

    public double heightToCm(int feet, int inches) {
        return (feet * 30.48d) + (inches * 2.54d);
    }

    // Default Mifflin-St Jeor BMR for backward compatibility
    public double calculateBMR(double weightLbs, int heightFeet, int heightInches, int age, String sex) {
        return calculateBMRByFormula("mifflin-st-jeor", weightLbs, heightFeet, heightInches, age, sex, null);
    }

    // Multi-formula BMR support
    public double calculateBMRByFormula(String formula,
                                        double weightLbs,
                                        int heightFeet,
                                        int heightInches,
                                        int age,
                                        String sex,
                                        Double bodyFatPercentage) {
        String f = (formula == null || formula.isBlank()) ? "mifflin-st-jeor" : formula.toLowerCase();
        double weightKg = poundsToKg(weightLbs);
        double heightCm = heightToCm(heightFeet, heightInches);

        switch (f) {
            case "harris-benedict":
                if (sex != null && sex.equalsIgnoreCase("male")) {
                    return 88.362 + (13.397 * weightKg) + (4.799 * heightCm) - (5.677 * age);
                } else {
                    return 447.593 + (9.247 * weightKg) + (3.098 * heightCm) - (4.330 * age);
                }
            case "katch-mcardle":
                if (bodyFatPercentage == null) {
                    throw new IllegalArgumentException("Body fat percentage is required for Katch-McArdle formula");
                }
                if (bodyFatPercentage < 5 || bodyFatPercentage > 50) {
                    throw new IllegalArgumentException("Body fat percentage must be between 5% and 50%");
                }
                double leanBodyMassKg = weightKg * (1 - (bodyFatPercentage / 100.0));
                return 370 + (21.6 * leanBodyMassKg);
            case "mifflin-st-jeor":
            default:
                double base = (10 * weightKg) + (6.25 * heightCm) - (5 * age);
                if (sex != null && sex.equalsIgnoreCase("female")) {
                    return base - 161;
                }
                return base + 5;
        }
    }

    // activityLevel options mapped to multipliers; support legacy labels as synonyms
    public double calculateTDEE(double bmr, String activityLevel) {
        String lvl = activityLevel == null ? "" : activityLevel.toLowerCase().trim();
        double multiplier;
        switch (lvl) {
            case "sedentary" -> multiplier = 1.2;
            case "occasionally active" -> multiplier = 1.375;
            case "lightly active" -> multiplier = 1.375; // legacy/simple synonym
            case "lightly active (exercise 1-2x a week)" -> multiplier = 1.375; // legacy label
            case "moderately active" -> multiplier = 1.55;
            case "moderately active(exercise 3x a week)" -> multiplier = 1.55; // legacy label
            case "very active" -> multiplier = 1.725;
            case "very active(exercise 4-5x a week)" -> multiplier = 1.725; // legacy label
            case "extremely active" -> multiplier = 1.9;
            case "extremely active (exercise 5-7x a week)" -> multiplier = 1.9; // legacy label
            default -> multiplier = 1.2;
        }
        return bmr * multiplier;
    }

    // goal: Lose Weight, Maintain Weight, Build Muscle
    public double adjustForGoal(double tdee, String goal) {
        if (goal == null) return tdee;
        String g = goal.toLowerCase();
        if (g.contains("lose")) {
            // Updated: more aggressive deficit
            return tdee - 500;
        } else if (g.contains("build") || g.contains("gain")) {
            // Updated: slightly less aggressive surplus
            return tdee + 300;
        }
        return tdee; // maintain
    }

    // --- Daily targets for a plan ---

    /** Length of the gradual-start ramp. Week 4 is the full target. */
    public static final int RAMP_WEEKS = 4;
    static final double FAT_LOSS_DEFICIT = 0.20;
    static final double BUILD_SURPLUS = 0.10;
    static final int MIN_CALORIES_FEMALE = 1200;
    static final int MIN_CALORIES_MALE = 1500;
    static final double MIN_PROTEIN_PER_LB = 0.5;
    static final double MIN_FAT_PER_LB = 0.3;
    static final double MIN_FAT_SHARE = 0.20;
    static final int MIN_CARBS = 75;
    // About 0.8 g per kg: a common ceiling for kidney disease until a doctor says otherwise.
    static final double KIDNEY_MAX_PROTEIN_PER_LB = 0.36;
    static final double FIBER_PER_1000_KCAL = 14;

    /** Ramp week (1 to 4) for a ramp that started on {@code start}, or null once it's finished. */
    public static Integer rampWeek(java.time.LocalDate start, java.time.LocalDate today) {
        if (start == null || today == null) return null;
        long days = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(start, today));
        int week = (int) (days / 7) + 1;
        return week > RAMP_WEEKS ? null : week;
    }

    /**
     * Calories, macros, fiber and water for one client. Calories come from the goal
     * (and health screen); the macro style only decides how those calories are split.
     *
     * @param rampWeek 1 to 4 to ease in gradually, or null for the full target
     */
    public NutritionTargets buildTargets(UserProfile p, Integer rampWeek) {
        double bmr = calculateBMRByFormula(p.getBmrFormula(), p.getWeight(), p.getHeightFeet(), p.getHeightInches(),
                p.getAge(), p.getSex(), p.getBodyFatPercentage());
        double tdee = calculateTDEE(bmr, p.getActivityLevel());
        boolean female = "female".equalsIgnoreCase(p.getSex());
        Set<HealthCondition> health = p.getHealthConditions();
        boolean healthHold = !health.isEmpty() && !p.isHealthCleared();
        String goal = goalKey(p.getFitnessGoal());
        Integer week = (rampWeek == null || rampWeek < 1) ? null : Math.min(rampWeek, RAMP_WEEKS);

        NutritionTargets t = new NutritionTargets();
        double calories = tdee;
        if (!healthHold && goal.equals("lose")) {
            double deficit = week == null ? FAT_LOSS_DEFICIT : FAT_LOSS_DEFICIT * week / RAMP_WEEKS;
            double planned = tdee * (1 - deficit);
            double floor = Math.max(bmr, female ? MIN_CALORIES_FEMALE : MIN_CALORIES_MALE);
            // Never below resting burn or the minimums, and never above maintenance.
            calories = Math.min(tdee, Math.max(planned, floor));
            t.setFloorApplied(calories > planned + 0.5);
        } else if (!healthHold && goal.equals("build")) {
            calories = tdee * (1 + BUILD_SURPLUS);
        }

        double ref = referenceWeight(p);
        double protein;
        double fat;
        if (UserProfile.STYLE_CUSTOM.equals(p.getMacroStyle())) {
            protein = calories * p.getCustomProteinPercent() / 100.0 / 4;
            fat = calories * p.getCustomFatPercent() / 100.0 / 9;
        } else {
            protein = proteinPerLb(p.getMacroStyle()) * ref;
            fat = calories * fatShare(p.getMacroStyle()) / 9;
            // Keep protein from crowding out everything else on a small calorie budget.
            protein = Math.min(protein, calories * 0.40 / 4);
        }
        if (week != null) {
            // Ease protein up gently: 85% of the target in week 1, up to 100% in week 4.
            // Steeper than this leaves protein very low while calories are near maintenance.
            protein *= 0.85 + 0.05 * (week - 1);
        }

        boolean kidney = health.contains(HealthCondition.KIDNEY) && !p.isHealthCleared();
        double proteinFloor = kidney ? 0 : MIN_PROTEIN_PER_LB * ref;
        double fatFloor = Math.max(MIN_FAT_PER_LB * ref, calories * MIN_FAT_SHARE / 9);
        protein = Math.max(protein, proteinFloor);
        if (kidney) protein = Math.min(protein, KIDNEY_MAX_PROTEIN_PER_LB * ref);
        fat = Math.max(fat, fatFloor);

        // Carbs fill the rest. If that leaves too few, take from fat, then protein, down to their floors.
        double carbs = (calories - protein * 4 - fat * 9) / 4;
        if (carbs < MIN_CARBS) {
            double fromFat = Math.min(fat - fatFloor, (MIN_CARBS - carbs) * 4 / 9);
            fat -= fromFat;
            carbs += fromFat * 9 / 4;
        }
        if (carbs < MIN_CARBS) {
            double fromProtein = Math.min(protein - proteinFloor, MIN_CARBS - carbs);
            protein -= fromProtein;
            carbs += fromProtein;
        }
        carbs = Math.max(0, carbs);

        t.setCalories((int) Math.round(calories));
        t.setProtein((int) Math.round(protein));
        t.setCarbs((int) Math.round(carbs));
        t.setFat((int) Math.round(fat));
        t.setFiber((int) Math.round(calories * FIBER_PER_1000_KCAL / 1000));
        t.setWaterOz((int) Math.round(p.getWeight() / 2));
        t.setBmr((int) Math.round(bmr));
        t.setMaintenanceCalories((int) Math.round(tdee));
        t.setReferenceWeight(Math.round(ref * 10) / 10.0);
        t.setRampWeek(week);
        t.setHealthHold(healthHold);
        for (HealthCondition c : health) {
            t.getHealthLabels().add(c.getLabel());
            if (c.isCreatineNeedsDoctor()) t.setCreatineNeedsDoctor(true);
        }
        return t;
    }

    /**
     * The weight protein and fat are scaled to: the goal weight if set, otherwise the
     * current weight, except above a BMI of 30 where the weight at a BMI of 25 is used
     * so protein doesn't come out unrealistically high.
     */
    static double referenceWeight(UserProfile p) {
        Double goal = p.getGoalWeight();
        if (goal != null && goal >= 70 && goal <= 700) return goal;
        double inches = p.getHeightFeet() * 12 + p.getHeightInches();
        if (inches <= 0) return p.getWeight();
        double bmi = 703 * p.getWeight() / (inches * inches);
        return bmi > 30 ? 25 * inches * inches / 703 : p.getWeight();
    }

    static String goalKey(String goal) {
        String g = goal == null ? "" : goal.toLowerCase();
        if (g.contains("lose")) return "lose";
        if (g.contains("build") || g.contains("gain")) return "build";
        return "maintain";
    }

    private static double proteinPerLb(String style) {
        if (UserProfile.STYLE_HIGH_PROTEIN.equals(style)) return 1.0;
        if (UserProfile.STYLE_HIGH_CARB.equals(style)) return 0.6;
        if (UserProfile.STYLE_LOW_CARB.equals(style)) return 0.8;
        return 0.7;
    }

    private static double fatShare(String style) {
        if (UserProfile.STYLE_HIGH_PROTEIN.equals(style)) return 0.25;
        if (UserProfile.STYLE_HIGH_CARB.equals(style)) return 0.20;
        if (UserProfile.STYLE_LOW_CARB.equals(style)) return 0.40;
        return 0.30;
    }

    public MacroTargets calculateMacros(double targetCalories) {
        // Backward compatibility: default to maintenance split
        return calculateMacrosByGoal(targetCalories, "maintain");
    }

    // New: Goal-specific macro calculation with updated splits
    public MacroTargets calculateMacrosByGoal(double totalCalories, String goal) {
        String g = goal == null ? "maintain" : goal.toLowerCase();
        int p, c, f;
        if (g.contains("lose")) {
            p = 35; c = 35; f = 30; // 35/35/30 for weight loss
        } else if (g.contains("build") || g.contains("gain")) {
            p = 30; c = 40; f = 30; // 30/40/30 for build
        } else {
            p = 30; c = 40; f = 30; // 30/40/30 maintain
        }
        return calculateMacros(totalCalories, p, c, f);
    }

    // Customizable macro split using percentages (P/C/F must sum to 100)
    public MacroTargets calculateMacros(double totalCalories, int proteinPercent, int carbsPercent, int fatPercent) {
        int total = proteinPercent + carbsPercent + fatPercent;
        if (total != 100) {
            throw new IllegalArgumentException("Macro percentages must total 100%. Current total: " + total + "%");
        }
        if (proteinPercent < 20 || proteinPercent > 50) {
            throw new IllegalArgumentException("Protein must be between 20% and 50%. Current: " + proteinPercent + "%");
        }
        if (carbsPercent < 20 || carbsPercent > 60) {
            throw new IllegalArgumentException("Carbs must be between 20% and 60%. Current: " + carbsPercent + "%");
        }
        if (fatPercent < 15 || fatPercent > 45) {
            throw new IllegalArgumentException("Fat must be between 15% and 45%. Current: " + fatPercent + "%");
        }

        double proteinCalories = totalCalories * proteinPercent / 100.0;
        double carbCalories = totalCalories * carbsPercent / 100.0;
        double fatCalories = totalCalories * fatPercent / 100.0;

        int protein = (int) Math.round(proteinCalories / 4.0);
        int carbs = (int) Math.round(carbCalories / 4.0);
        int fat = (int) Math.round(fatCalories / 9.0);
        int calories = (int) Math.round(totalCalories);
        return new MacroTargets(calories, protein, carbs, fat);
    }
}
