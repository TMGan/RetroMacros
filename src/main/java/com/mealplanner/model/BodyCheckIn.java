package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One day's body check-in. Weight is required; everything else is optional.
 * At most one per client per day: saving again on the same day updates it.
 */
@Entity
@Table(name = "body_check_ins",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "check_in_date"}),
        indexes = @Index(name = "idx_check_in_user_date", columnList = "user_id, check_in_date"))
public class BodyCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "weight_lbs", nullable = false)
    private Double weightLbs;

    @Column(name = "body_fat_pct")
    private Double bodyFatPct;

    // Legacy: measurements now live in MeasurementEntry. Old values are moved there on startup
    // (LegacyMeasurementMigration) and these columns are no longer written.
    private Double waistIn;
    private Double hipsIn;
    private Double chestIn;
    private Double armIn;
    private Double thighIn;

    // How they feel, 1 (low) to 5 (high). For hunger and stress, higher means more.
    private Integer energy;
    private Integer sleep;
    private Integer hunger;
    private Integer stress;

    @Column(length = 500)
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BodyCheckIn() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
    public Double getWeightLbs() { return weightLbs; }
    public void setWeightLbs(Double weightLbs) { this.weightLbs = weightLbs; }
    public Double getBodyFatPct() { return bodyFatPct; }
    public void setBodyFatPct(Double bodyFatPct) { this.bodyFatPct = bodyFatPct; }
    public Double getWaistIn() { return waistIn; }
    public void setWaistIn(Double waistIn) { this.waistIn = waistIn; }
    public Double getHipsIn() { return hipsIn; }
    public void setHipsIn(Double hipsIn) { this.hipsIn = hipsIn; }
    public Double getChestIn() { return chestIn; }
    public void setChestIn(Double chestIn) { this.chestIn = chestIn; }
    public Double getArmIn() { return armIn; }
    public void setArmIn(Double armIn) { this.armIn = armIn; }
    public Double getThighIn() { return thighIn; }
    public void setThighIn(Double thighIn) { this.thighIn = thighIn; }
    public Integer getEnergy() { return energy; }
    public void setEnergy(Integer energy) { this.energy = energy; }
    public Integer getSleep() { return sleep; }
    public void setSleep(Integer sleep) { this.sleep = sleep; }
    public Integer getHunger() { return hunger; }
    public void setHunger(Integer hunger) { this.hunger = hunger; }
    public Integer getStress() { return stress; }
    public void setStress(Integer stress) { this.stress = stress; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
