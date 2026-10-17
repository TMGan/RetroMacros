package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    private String profilePictureUrl;

    @Column(unique = true, nullable = false)
    private String googleId;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @Column(name = "is_admin")
    private boolean isAdmin = false;

    // Optional timezone preference for admin/user pages
    private String timezone;

    // Tracks activity used in admin views
    private LocalDateTime lastActiveAt;

    // Target weight for the progress page. The client sets it; the coach can change it.
    @Column(name = "goal_weight_lbs")
    private Double goalWeightLbs;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavedMealPlan> savedMealPlans = new ArrayList<>();

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    public User(String email, String name, String googleId) {
        this();
        this.email = email;
        this.name = name;
        this.googleId = googleId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public List<SavedMealPlan> getSavedMealPlans() { return savedMealPlans; }
    public void setSavedMealPlans(List<SavedMealPlan> savedMealPlans) { this.savedMealPlans = savedMealPlans; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public Double getGoalWeightLbs() { return goalWeightLbs; }
    public void setGoalWeightLbs(Double goalWeightLbs) { this.goalWeightLbs = goalWeightLbs; }
}
