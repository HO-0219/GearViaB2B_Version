package com.teamproject.aiusage.domain;

public record AiUsageTotals(
        Long requests,
        Long failedRequests,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens
) {
}
