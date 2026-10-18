package com.mealplanner.model;

import java.util.ArrayList;
import java.util.List;

public class Meal {
    private String name; // e.g., Breakfast, Lunch, Dinner, Snack
    private List<FoodItem> foods;
    private MacroTargets macros;
    private Recipe recipe;
    // breakfast, lunch, dinner or snack. Decides which meals must include a vegetable.
    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Meal() {
        this.foods = new ArrayList<>();
    }

    public Meal(String name, List<FoodItem> foods, MacroTargets macros) {
        this.name = name;
        this.foods = foods != null ? foods : new ArrayList<>();
        this.macros = macros;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<FoodItem> getFoods() {
        return foods;
    }

    public void setFoods(List<FoodItem> foods) {
        this.foods = foods;
    }

    public MacroTargets getMacros() {
        return macros;
    }

    public void setMacros(MacroTargets macros) {
        this.macros = macros;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }
}
