package com.mealplanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class MealPlan {
    private List<Day> days;
    private MacroTargets dailyTargets;
    // Problems the code checks still found after a retry. For the coach; not part of the plan JSON.
    @JsonIgnore
    private List<String> qualityNotes = new ArrayList<>();

    @JsonIgnore
    public List<String> getQualityNotes() {
        return qualityNotes;
    }

    public void setQualityNotes(List<String> qualityNotes) {
        this.qualityNotes = qualityNotes;
    }

    public MealPlan() {
        this.days = new ArrayList<>();
    }

    public MealPlan(List<Day> days, MacroTargets dailyTargets) {
        this.days = days != null ? days : new ArrayList<>();
        this.dailyTargets = dailyTargets;
    }

    public List<Day> getDays() {
        return days;
    }

    public void setDays(List<Day> days) {
        this.days = days;
    }

    public MacroTargets getDailyTargets() {
        return dailyTargets;
    }

    public void setDailyTargets(MacroTargets dailyTargets) {
        this.dailyTargets = dailyTargets;
    }
}

