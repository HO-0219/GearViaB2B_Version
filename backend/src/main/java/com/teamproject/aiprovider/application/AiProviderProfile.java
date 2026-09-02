package com.teamproject.aiprovider.application;

public record AiProviderProfile(
        Provider provider,
        String baseUrl,
        String chatModel,
        String embeddingModel,
        int requestTimeoutSeconds,
        boolean externalAllowed) {
    public enum Provider { OPENAI, INTERNAL_OPENAI_COMPATIBLE }
}
