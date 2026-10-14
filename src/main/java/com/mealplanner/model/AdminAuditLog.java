package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Record of a sensitive admin action, such as viewing a client account. */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String adminEmail;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 255)
    private String target;

    private LocalDateTime createdAt;

    public AdminAuditLog() {
        this.createdAt = LocalDateTime.now();
    }

    public AdminAuditLog(String adminEmail, String action, String target) {
        this();
        this.adminEmail = adminEmail;
        this.action = action;
        this.target = target;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
