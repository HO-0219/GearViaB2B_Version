package com.teamproject.aiusage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_records")
public class AiUsageRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiUsageOperation operation;

    @Column(nullable = false, length = 120)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiUsageOutcome outcome;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected AiUsageRecord() {
    }

    private AiUsageRecord(AiUsageOperation operation, String model, AiUsageOutcome outcome,
                          Long inputTokens, Long outputTokens, Long totalTokens,
                          String failureCode, LocalDateTime occurredAt) {
        this.operation = operation;
        this.model = model;
        this.outcome = outcome;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.failureCode = failureCode;
        this.occurredAt = occurredAt;
    }

    public static AiUsageRecord success(AiUsageOperation operation, String model,
                                        Long inputTokens, Long outputTokens, Long totalTokens,
                                        LocalDateTime occurredAt) {
        return new AiUsageRecord(operation, model, AiUsageOutcome.SUCCESS,
                inputTokens, outputTokens, totalTokens, null, occurredAt);
    }

    public static AiUsageRecord failure(AiUsageOperation operation, String model,
                                        String failureCode, LocalDateTime occurredAt) {
        return new AiUsageRecord(operation, model, AiUsageOutcome.FAILURE,
                null, null, null, failureCode, occurredAt);
    }

    public Long getId() { return id; }
    public AiUsageOperation getOperation() { return operation; }
    public String getModel() { return model; }
    public AiUsageOutcome getOutcome() { return outcome; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public String getFailureCode() { return failureCode; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
