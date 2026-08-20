package com.teamproject.common.storage;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupFeaturePolicy;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.project.domain.ProjectDocumentRepository;
import com.teamproject.project.domain.ProjectIssueImageRepository;
import com.teamproject.resource.domain.GroupResourceRepository;
import com.teamproject.chat.domain.ChatMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GroupStorageQuotaService {
    private final GroupRepository groups;
    private final GroupFeaturePolicy features;
    private final GroupResourceRepository resources;
    private final ProjectDocumentRepository documents;
    private final ProjectIssueImageRepository images;
    private final ChatMessageRepository chatMessages;

    public GroupStorageQuotaService(GroupRepository groups, GroupFeaturePolicy features,
            GroupResourceRepository resources, ProjectDocumentRepository documents,
            ProjectIssueImageRepository images, ChatMessageRepository chatMessages) {
        this.groups = groups; this.features = features; this.resources = resources;
        this.documents = documents; this.images = images; this.chatMessages = chatMessages;
    }

    public Usage usage(Long userId, Long groupId) {
        long limit = features.policy(userId, groupId).storageLimitBytes();
        long used = resources.sumActiveFileBytesByGroupId(groupId)
                + documents.sumActiveFileBytesByGroupId(groupId) + images.sumActiveBytesByGroupId(groupId)
                + chatMessages.sumAttachmentBytesByGroupId(groupId);
        return new Usage(used, limit, Math.max(0, limit - used));
    }

    public void requireCapacity(Long userId, Long groupId, long incomingBytes) {
        // Every upload transaction locks the group row before checking aggregate usage.
        groups.findByIdForUpdate(groupId).orElseThrow(() -> new ApplicationException(
                "GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."));
        Usage usage = usage(userId, groupId);
        if (incomingBytes > usage.remainingBytes()) throw new ApplicationException(
                "GROUP_STORAGE_LIMIT_EXCEEDED", HttpStatus.CONFLICT, "그룹 저장공간 한도를 초과합니다.");
    }

    public record Usage(long usedBytes, long limitBytes, long remainingBytes) {}
}
