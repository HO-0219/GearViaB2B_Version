package com.teamproject.assistant.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantEntitlementService {
    private final GroupAuthorization authorization;

    public AiAssistantEntitlementService(GroupAuthorization authorization) {
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public void require(Long userId, Long groupId) {
        Group group = authorization.requireActiveMember(groupId, userId).getGroup();
        boolean activePaidPeriod = group.getType() == Group.Type.TEAM
                && group.getMembershipPlan() == Group.MembershipPlan.PAID
                && group.getPaidUntil() != null
                && group.getPaidUntil().isAfter(LocalDateTime.now());
        if (!activePaidPeriod) {
            throw new ApplicationException("AI_ASSISTANT_POLICY_REQUIRED", HttpStatus.FORBIDDEN,
                    "AI 비서는 관리자 정책에서 허용된 팀 그룹에서 사용할 수 있습니다.");
        }
    }
}
