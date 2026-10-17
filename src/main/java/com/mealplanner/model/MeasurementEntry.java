package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One tape-measure session, in inches. Separate from the daily weigh-in because
 * measurements change slowly; clients are reminded every 4 weeks. At most one per
 * client per day: saving again on the same day updates it.
 */
@Entity
@Table(name = "body_measurements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "measured_on"}),
        indexes = @Index(name = "idx_measurement_user_date", columnList = "user_id, measured_on"))
public class MeasurementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    private Double neckIn;
    private Double shouldersIn;
    private Double chestIn;
    private Double armRightIn;
    private Double armLeftIn;
    private Double waistIn;
    private Double hipsIn;
    private Double thighRightIn;
    private Double thighLeftIn;
    private Double calfRightIn;
    private Double calfLeftIn;

    private LocalDateTime createdAt;

    public MeasurementEntry() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isEmpty() {
        for (BodyPart part : BodyPart.values()) {
            if (part.get(this) != null) return false;
        }
        return true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDate getMeasuredOn() { return measuredOn; }
    public void setMeasuredOn(LocalDate measuredOn) { this.measuredOn = measuredOn; }
    public Double getNeckIn() { return neckIn; }
    public void setNeckIn(Double neckIn) { this.neckIn = neckIn; }
    public Double getShouldersIn() { return shouldersIn; }
    public void setShouldersIn(Double shouldersIn) { this.shouldersIn = shouldersIn; }
    public Double getChestIn() { return chestIn; }
    public void setChestIn(Double chestIn) { this.chestIn = chestIn; }
    public Double getArmRightIn() { return armRightIn; }
    public void setArmRightIn(Double armRightIn) { this.armRightIn = armRightIn; }
    public Double getArmLeftIn() { return armLeftIn; }
    public void setArmLeftIn(Double armLeftIn) { this.armLeftIn = armLeftIn; }
    public Double getWaistIn() { return waistIn; }
    public void setWaistIn(Double waistIn) { this.waistIn = waistIn; }
    public Double getHipsIn() { return hipsIn; }
    public void setHipsIn(Double hipsIn) { this.hipsIn = hipsIn; }
    public Double getThighRightIn() { return thighRightIn; }
    public void setThighRightIn(Double thighRightIn) { this.thighRightIn = thighRightIn; }
    public Double getThighLeftIn() { return thighLeftIn; }
    public void setThighLeftIn(Double thighLeftIn) { this.thighLeftIn = thighLeftIn; }
    public Double getCalfRightIn() { return calfRightIn; }
    public void setCalfRightIn(Double calfRightIn) { this.calfRightIn = calfRightIn; }
    public Double getCalfLeftIn() { return calfLeftIn; }
    public void setCalfLeftIn(Double calfLeftIn) { this.calfLeftIn = calfLeftIn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
