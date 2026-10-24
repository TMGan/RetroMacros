package com.mealplanner.model;

import java.util.*;

/**
 * Conditions on the plan form's health screen. Each is passed to the AI so it can plan around it.
 * Most also (until the coach clears the client) set calories to maintenance and flag the
 * client for review. A GLP-1 medication is prescribed and managed by a doctor already, so it
 * only changes the plan and shows the coach a badge.
 */
public enum HealthCondition {
    PREGNANT("pregnant", "Pregnant or breastfeeding", true, true),
    KIDNEY("kidney", "Kidney disease or reduced kidney function", true, true),
    DIABETES_MEDS("diabetes", "Diabetes treated with insulin or other medication", true, true),
    EATING_DISORDER("eating-disorder", "Current or past eating disorder", false, true),
    GLP1("glp1", "Taking a GLP-1 medication (like Ozempic, Wegovy, Mounjaro or Zepbound)", false, false);

    private final String key;
    private final String label;
    // Whether creatine should be cleared with a doctor first.
    private final boolean creatineNeedsDoctor;
    // Whether it holds plans at maintenance until the coach reviews the client.
    private final boolean needsReview;

    HealthCondition(String key, String label, boolean creatineNeedsDoctor, boolean needsReview) {
        this.key = key;
        this.label = label;
        this.creatineNeedsDoctor = creatineNeedsDoctor;
        this.needsReview = needsReview;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public boolean isCreatineNeedsDoctor() { return creatineNeedsDoctor; }
    public boolean isNeedsReview() { return needsReview; }

    /** True when any of the conditions needs the coach's review. */
    public static boolean needsReview(Collection<HealthCondition> conditions) {
        return conditions.stream().anyMatch(HealthCondition::isNeedsReview);
    }

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
