package com.teamproject.aiusage.domain;

public record AiUsageBreakdown(
        AiUsageOperation operation,
        String model,
        Long requests,
        Long failedRequests,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens
) {
}
