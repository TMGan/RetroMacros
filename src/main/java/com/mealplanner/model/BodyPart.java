package com.mealplanner.model;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The spots a client can measure, with sane input limits (inches) and where each
 * sits on the body diagram. Diagram coordinates use a 200 x 440 viewBox; the
 * figure faces the viewer, so the client's right side is on the viewer's left.
 */
public enum BodyPart {
    //        key           label           min  max  side     labelY  male point  female point
    NECK("neck", "Neck", 8, 30, "left", 50, 100, 56, 100, 56, MeasurementEntry::getNeckIn, MeasurementEntry::setNeckIn),
    SHOULDERS("shoulders", "Shoulders", 25, 80, "right", 72, 136, 72, 128, 74, MeasurementEntry::getShouldersIn, MeasurementEntry::setShouldersIn),
    CHEST("chest", "Chest", 20, 70, "left", 106, 100, 104, 100, 106, MeasurementEntry::getChestIn, MeasurementEntry::setChestIn),
    ARM_RIGHT("armRight", "Right arm", 6, 30, "left", 160, 47, 128, 55, 130, MeasurementEntry::getArmRightIn, MeasurementEntry::setArmRightIn),
    ARM_LEFT("armLeft", "Left arm", 6, 30, "right", 136, 153, 128, 145, 130, MeasurementEntry::getArmLeftIn, MeasurementEntry::setArmLeftIn),
    WAIST("waist", "Waist", 15, 80, "left", 214, 100, 168, 100, 168, MeasurementEntry::getWaistIn, MeasurementEntry::setWaistIn),
    HIPS("hips", "Hips", 20, 80, "right", 200, 126, 204, 134, 208, MeasurementEntry::getHipsIn, MeasurementEntry::setHipsIn),
    THIGH_RIGHT("thighRight", "Right thigh", 10, 45, "left", 268, 84, 262, 82, 264, MeasurementEntry::getThighRightIn, MeasurementEntry::setThighRightIn),
    THIGH_LEFT("thighLeft", "Left thigh", 10, 45, "right", 264, 116, 262, 118, 264, MeasurementEntry::getThighLeftIn, MeasurementEntry::setThighLeftIn),
    CALF_RIGHT("calfRight", "Right calf", 8, 30, "left", 340, 85, 348, 85, 348, MeasurementEntry::getCalfRightIn, MeasurementEntry::setCalfRightIn),
    CALF_LEFT("calfLeft", "Left calf", 8, 30, "right", 330, 115, 348, 115, 348, MeasurementEntry::getCalfLeftIn, MeasurementEntry::setCalfLeftIn);

    private final String key;
    private final String label;
    private final double min;
    private final double max;
    private final String side;
    private final int labelY;
    private final int maleX, maleY, femaleX, femaleY;
    private final Function<MeasurementEntry, Double> getter;
    private final BiConsumer<MeasurementEntry, Double> setter;

    BodyPart(String key, String label, double min, double max, String side, int labelY,
             int maleX, int maleY, int femaleX, int femaleY,
             Function<MeasurementEntry, Double> getter, BiConsumer<MeasurementEntry, Double> setter) {
        this.key = key;
        this.label = label;
        this.min = min;
        this.max = max;
        this.side = side;
        this.labelY = labelY;
        this.maleX = maleX;
        this.maleY = maleY;
        this.femaleX = femaleX;
        this.femaleY = femaleY;
        this.getter = getter;
        this.setter = setter;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public String getSide() { return side; }
    public int getLabelY() { return labelY; }
    public int getMaleX() { return maleX; }
    public int getMaleY() { return maleY; }
    public int getFemaleX() { return femaleX; }
    public int getFemaleY() { return femaleY; }
    /** Where the leader line meets the diagram edge: the left or right side of the figure. */
    public int getEdgeX() { return "left".equals(side) ? 0 : 200; }
    public int getNumber() { return ordinal() + 1; }

    public Double get(MeasurementEntry entry) { return getter.apply(entry); }
    public void set(MeasurementEntry entry, Double value) { setter.accept(entry, value); }
}
