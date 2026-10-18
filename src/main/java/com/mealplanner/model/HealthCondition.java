package com.mealplanner.model;

import java.util.*;

/**
 * Conditions on the plan form's health screen. Any of them (until the coach clears
 * the client) sets calories to maintenance, flags the client for the coach, and is
 * passed to the AI so it can plan around it.
 */
public enum HealthCondition {
    PREGNANT("pregnant", "Pregnant or breastfeeding", true),
    KIDNEY("kidney", "Kidney disease or reduced kidney function", true),
    DIABETES_MEDS("diabetes", "Diabetes treated with insulin or other medication", true),
    EATING_DISORDER("eating-disorder", "Current or past eating disorder", false);

    private final String key;
    private final String label;
    // Whether creatine should be cleared with a doctor first.
    private final boolean creatineNeedsDoctor;

    HealthCondition(String key, String label, boolean creatineNeedsDoctor) {
        this.key = key;
        this.label = label;
        this.creatineNeedsDoctor = creatineNeedsDoctor;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public boolean isCreatineNeedsDoctor() { return creatineNeedsDoctor; }

    public static Optional<HealthCondition> fromKey(String key) {
        for (HealthCondition c : values()) {
            if (c.key.equals(key)) return Optional.of(c);
        }
        return Optional.empty();
    }

    /** Parses a stored comma-separated list of keys, ignoring anything unknown. */
    public static Set<HealthCondition> parse(String csv) {
        Set<HealthCondition> out = EnumSet.noneOf(HealthCondition.class);
        if (csv == null || csv.isBlank()) return out;
        for (String key : csv.split(",")) fromKey(key.trim()).ifPresent(out::add);
        return out;
    }

    public static String join(Collection<HealthCondition> conditions) {
        StringJoiner j = new StringJoiner(",");
        for (HealthCondition c : conditions) j.add(c.key);
        return j.toString();
    }
}
