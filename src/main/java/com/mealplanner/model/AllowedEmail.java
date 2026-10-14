package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** An email address that is allowed to sign in when the app is invite-only. */
@Entity
@Table(name = "allowed_emails")
public class AllowedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 200)
    private String note;

    private LocalDateTime invitedAt;

    public AllowedEmail() {
        this.invitedAt = LocalDateTime.now();
    }

    public AllowedEmail(String email, String note) {
        this();
        this.email = email;
        this.note = note;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime invitedAt) { this.invitedAt = invitedAt; }
}
