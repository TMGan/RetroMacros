package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** How many AI-backed requests of one kind a user has made on one day. */
@Entity
@Table(name = "ai_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "kind", "usage_date"}))
public class AiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    public AiUsage() {}

    public AiUsage(User user, String kind, LocalDate usageDate) {
        this.user = user;
        this.kind = kind;
        this.usageDate = usageDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }
    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }
}
