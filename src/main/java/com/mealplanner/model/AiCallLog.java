package com.mealplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One request to the AI provider: who made it, what it used, and what it cost. */
@Entity
@Table(name = "ai_call_log", indexes = {
        @Index(name = "idx_ai_call_log_created", columnList = "created_at"),
        @Index(name = "idx_ai_call_log_user", columnList = "user_id")
})
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable: kept for cost history after a user is deleted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    /** Cost in millionths of a US dollar, so sums stay exact. */
    @Column(name = "cost_micros", nullable = false)
    private long costMicros;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_type", length = 60)
    private String errorType;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiCallLog() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
    public long getCostMicros() { return costMicros; }
    public void setCostMicros(long costMicros) { this.costMicros = costMicros; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
