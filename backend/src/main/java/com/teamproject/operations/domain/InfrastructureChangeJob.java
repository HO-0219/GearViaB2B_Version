package com.teamproject.operations.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "infrastructure_change_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_infrastructure_change_correlation", columnNames = "correlation_id"),
        indexes = @Index(name = "idx_infrastructure_change_status_updated", columnList = "status, updated_at, id"))
public class InfrastructureChangeJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Column(name = "redacted_target", nullable = false, length = 500)
    private String redactedTarget;

    @Column(name = "estimated_seconds", nullable = false)
    private long estimatedSeconds;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(name = "verification_summary", length = 2000)
    private String verificationSummary;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "rollback_summary", length = 2000)
    private String rollbackSummary;

    @Column(name = "correlation_id", nullable = false, length = 80)
    private String correlationId;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected InfrastructureChangeJob() {}

    public InfrastructureChangeJob(Type type, User actor, String redactedTarget, long estimatedSeconds,
            String correlationId) {
        this.type = type;
        this.actor = actor;
        this.redactedTarget = redactedTarget;
        this.estimatedSeconds = estimatedSeconds;
        this.correlationId = correlationId;
        this.status = Status.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(Status target, int progressPercent, String summary) {
        LocalDateTime now = LocalDateTime.now();
        this.status = target;
        this.progressPercent = progressPercent;
        this.updatedAt = now;
        if (target == Status.TESTING && startedAt == null) {
            startedAt = now;
        }
        if (target == Status.FAILED) {
            failureCode = abbreviate(summary, 100);
        } else if (target == Status.ROLLING_BACK || target == Status.ROLLED_BACK) {
            rollbackSummary = abbreviate(summary, 2000);
        } else if (summary != null && !summary.isBlank()) {
            verificationSummary = abbreviate(summary, 2000);
        }
        if (target == Status.COMPLETED || target == Status.ROLLED_BACK) {
            completedAt = now;
        }
    }

    private String abbreviate(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public User getActor() { return actor; }
    public String getRedactedTarget() { return redactedTarget; }
    public long getEstimatedSeconds() { return estimatedSeconds; }
    public int getProgressPercent() { return progressPercent; }
    public String getVerificationSummary() { return verificationSummary; }
    public String getFailureCode() { return failureCode; }
    public String getRollbackSummary() { return rollbackSummary; }
    public String getCorrelationId() { return correlationId; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }

    public enum Type { MYSQL, STORAGE }

    public enum Status {
        DRAFT,
        TESTING,
        TEST_SUCCEEDED,
        SCHEDULED,
        NOTIFYING,
        MAINTENANCE,
        MIGRATING,
        VERIFYING,
        SWITCHED,
        COMPLETED,
        FAILED,
        ROLLING_BACK,
        ROLLED_BACK
    }
}
