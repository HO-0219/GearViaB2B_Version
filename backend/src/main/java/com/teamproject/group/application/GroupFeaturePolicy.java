package com.teamproject.group.application;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GroupFeaturePolicy {
    private final GroupAuthorization authorization;
    private final int freeMessageRetentionDays;
    private final int paidMessageRetentionDays;
    private final int paidChannelLimit;
    private final long freeStorageBytes;
    private final long paidStorageBytes;

    public GroupFeaturePolicy(GroupAuthorization authorization,
            @Value("${app.features.chat.free-retention-days:10}") int freeMessageRetentionDays,
            @Value("${app.features.chat.paid-retention-days:365}") int paidMessageRetentionDays,
            @Value("${app.features.chat.paid-channel-limit:50}") int paidChannelLimit,
            @Value("${app.features.storage.free-bytes:104857600}") long freeStorageBytes,
            @Value("${app.features.storage.paid-bytes:5368709120}") long paidStorageBytes) {
        this.authorization = authorization;
        this.freeMessageRetentionDays = freeMessageRetentionDays;
        this.paidMessageRetentionDays = paidMessageRetentionDays;
        this.paidChannelLimit = paidChannelLimit;
        this.freeStorageBytes = freeStorageBytes;
        this.paidStorageBytes = paidStorageBytes;
    }

    public FeaturePolicyResponse policy(Long userId, Long groupId) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        boolean paid = member.getGroup().getMembershipPlan() == Group.MembershipPlan.PAID;
        boolean team = member.getGroup().getType() == Group.Type.TEAM;
        return new FeaturePolicyResponse(groupId, member.getGroup().getMembershipPlan().name(),
                team, team && paid, team ? (paid ? paidChannelLimit : 1) : 0,
                team ? (paid ? paidMessageRetentionDays : freeMessageRetentionDays) : 0,
                paid ? paidStorageBytes : freeStorageBytes,
                paid ? 100L * 1024 * 1024 : 20L * 1024 * 1024);
    }

    public record FeaturePolicyResponse(Long groupId, String membershipPlan,
            boolean projectEnabled, boolean multipleChatChannels,
            int chatChannelLimit, int messageRetentionDays,
            long storageLimitBytes, long attachmentLimitBytes) {}
}
