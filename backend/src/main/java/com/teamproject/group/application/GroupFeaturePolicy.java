package com.teamproject.group.application;

import com.teamproject.organization.application.OrganizationFeaturePolicy;
import org.springframework.stereotype.Service;

@Service
public class GroupFeaturePolicy {
    private final OrganizationFeaturePolicy organizationPolicy;

    public GroupFeaturePolicy(OrganizationFeaturePolicy organizationPolicy) {
        this.organizationPolicy = organizationPolicy;
    }

    public FeaturePolicyResponse policy(Long userId, Long groupId) {
        return organizationPolicy.features(userId, groupId);
    }

    public record FeaturePolicyResponse(Long groupId, boolean projectEnabled, boolean multipleChatChannels,
            int chatChannelLimit, int messageRetentionDays,
            long storageLimitBytes, long attachmentLimitBytes) {}
}
