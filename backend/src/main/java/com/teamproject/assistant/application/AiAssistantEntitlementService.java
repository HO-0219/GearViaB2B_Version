package com.teamproject.assistant.application;

import com.teamproject.organization.application.OrganizationFeaturePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantEntitlementService {
    private final OrganizationFeaturePolicy policy;

    public AiAssistantEntitlementService(OrganizationFeaturePolicy policy) {
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public void require(Long userId, Long groupId) {
        policy.requireAiAssistant(userId, groupId);
    }
}
