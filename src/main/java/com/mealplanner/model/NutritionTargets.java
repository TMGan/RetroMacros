package com.mealplanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A client's daily targets plus the context behind them: what the plan page shows
 * next to the macros (fiber, water, ramp week, creatine) and why a number is what it is.
 */
public class NutritionTargets {

    private int calories;
    private int protein;
    private int carbs;
    private int fat;
    private int fiber;
    private int waterOz;
    private int bmr;
    private int maintenanceCalories;
    private double referenceWeight;
    // Null when the client isn't on a ramp.
    private Integer rampWeek;
    // True when a health-screen answer set calories to maintenance.
    private boolean healthHold;
    // True when the calorie floor raised the fat-loss target.
    private boolean floorApplied;
    private boolean creatineNeedsDoctor;
    private List<String> healthLabels = new ArrayList<>();

    public MacroTargets toMacroTargets() {
        MacroTargets t = new MacroTargets(calories, protein, carbs, fat);
        t.setFiber(fiber);
        return t;
    }

    /** Rebuilds what the plan page needs from a saved plan. */
    public static NutritionTargets fromSaved(SavedMealPlan saved) {
        NutritionTargets t = new NutritionTargets();
        t.calories = saved.getTargetCalories();
        t.protein = saved.getTargetProtein();
        t.carbs = saved.getTargetCarbs();
        t.fat = saved.getTargetFat();
        t.fiber = saved.getFiberTarget() == null ? Math.round(saved.getTargetCalories() * 14 / 1000f) : saved.getFiberTarget();
        t.waterOz = saved.getWaterOz() == null ? (int) Math.round(saved.getWeight() / 2) : saved.getWaterOz();
        t.rampWeek = saved.getRampWeek();
        t.healthHold = Boolean.TRUE.equals(saved.getHealthHold());
        for (HealthCondition c : HealthCondition.parse(saved.getHealthConditions())) {
            t.healthLabels.add(c.getLabel());
            if (c.isCreatineNeedsDoctor()) t.creatineNeedsDoctor = true;
        }
        return t;
    }

    public int getCalories() { return calories; }
    public void setCalories(int calories) { this.calories = calories; }
    public int getProtein() { return protein; }
    public void setProtein(int protein) { this.protein = protein; }
    public int getCarbs() { return carbs; }
    public void setCarbs(int carbs) { this.carbs = carbs; }
    public int getFat() { return fat; }
    public void setFat(int fat) { this.fat = fat; }
    public int getFiber() { return fiber; }
    public void setFiber(int fiber) { this.fiber = fiber; }
    public int getWaterOz() { return waterOz; }
    public void setWaterOz(int waterOz) { this.waterOz = waterOz; }
    public int getBmr() { return bmr; }
    public void setBmr(int bmr) { this.bmr = bmr; }
    public int getMaintenanceCalories() { return maintenanceCalories; }
    public void setMaintenanceCalories(int maintenanceCalories) { this.maintenanceCalories = maintenanceCalories; }
    public double getReferenceWeight() { return referenceWeight; }
    public void setReferenceWeight(double referenceWeight) { this.referenceWeight = referenceWeight; }
    public Integer getRampWeek() { return rampWeek; }
    public void setRampWeek(Integer rampWeek) { this.rampWeek = rampWeek; }
    public boolean isHealthHold() { return healthHold; }
    public void setHealthHold(boolean healthHold) { this.healthHold = healthHold; }
    public boolean isFloorApplied() { return floorApplied; }
    public void setFloorApplied(boolean floorApplied) { this.floorApplied = floorApplied; }
    public boolean isCreatineNeedsDoctor() { return creatineNeedsDoctor; }
    public void setCreatineNeedsDoctor(boolean creatineNeedsDoctor) { this.creatineNeedsDoctor = creatineNeedsDoctor; }
    public List<String> getHealthLabels() { return healthLabels; }
    public void setHealthLabels(List<String> healthLabels) { this.healthLabels = healthLabels; }
}
