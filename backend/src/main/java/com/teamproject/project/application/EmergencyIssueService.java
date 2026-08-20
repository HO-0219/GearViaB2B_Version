package com.teamproject.project.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.storage.ImageStorageService;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.project.application.dto.EmergencyIssueDtos.*;
import com.teamproject.project.domain.*;
import com.teamproject.task.domain.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class EmergencyIssueService {
    private final GroupAuthorization authorization; private final GroupMemberRepository members;
    private final ProjectRepository projects; private final EmergencyIssueRepository issues;
    private final TaskRepository tasks; private final NotificationService notifications;
    private final ImageStorageService images;
    public EmergencyIssueService(GroupAuthorization authorization, GroupMemberRepository members,
            ProjectRepository projects, EmergencyIssueRepository issues, TaskRepository tasks,
            NotificationService notifications, ImageStorageService images) {
        this.authorization=authorization; this.members=members; this.projects=projects; this.issues=issues;
        this.tasks=tasks; this.notifications=notifications; this.images=images;
    }
    @Transactional
    public Response create(Long userId, Long groupId, CreateRequest input) {
        GroupMember actor=authorization.requireActiveMember(groupId,userId);
        Project project=projects.findById(input.projectId()).filter(p->p.getGroup().getId().equals(groupId))
                .orElseThrow(()->new ApplicationException("PROJECT_NOT_FOUND",HttpStatus.NOT_FOUND,"프로젝트를 찾을 수 없습니다."));
        EmergencyIssue.Audience audience;
        try { audience=EmergencyIssue.Audience.valueOf(input.audience().trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new ApplicationException("EMERGENCY_AUDIENCE_INVALID",HttpStatus.BAD_REQUEST,"알림 대상을 선택해 주세요."); }
        EmergencyIssue issue=issues.save(new EmergencyIssue(actor.getGroup(),project,actor,input.title().trim(),blank(input.description()),audience));
        notifications.assistantMessage(actor, recipients(issue), "EMERGENCY_ISSUE:"+issue.getId(),
                "긴급 이슈 · "+project.getName(), issue.getTitle());
        return response(issue,actor);
    }
    @Transactional(readOnly=true)
    public List<Response> list(Long userId,Long groupId) {
        GroupMember actor=authorization.requireActiveMember(groupId,userId);
        return issues.findAllByGroupIdOrderByStatusAscCreatedAtDesc(groupId).stream().map(i->response(i,actor)).toList();
    }
    @Transactional
    public Response attachImage(Long userId,Long issueId,MultipartFile file) {
        EmergencyIssue issue=issue(issueId); GroupMember actor=authorization.requireActiveMember(issue.getGroup().getId(),userId);
        requireManage(issue,actor); String old=issue.getImageUrl(); issue.attachImage(images.store(file,"emergency-issues"));
        issues.flush(); images.deleteManagedAfterCommit(old); return response(issue,actor);
    }
    @Transactional
    public Response changeStatus(Long userId,Long issueId,StatusRequest input) {
        EmergencyIssue issue=issue(issueId); GroupMember actor=authorization.requireActiveMember(issue.getGroup().getId(),userId);
        requireManage(issue,actor); if(issue.getVersion()!=input.expectedVersion()) throw new ApplicationException("VERSION_CONFLICT",HttpStatus.CONFLICT,"다른 사용자가 먼저 변경했습니다. 새로고침 후 다시 시도해 주세요.");
        EmergencyIssue.Status status;
        try { status=EmergencyIssue.Status.valueOf(input.status().trim().toUpperCase()); }
        catch(IllegalArgumentException exception){throw new ApplicationException("EMERGENCY_STATUS_INVALID",HttpStatus.BAD_REQUEST,"올바른 상태를 선택해 주세요.");}
        issue.changeStatus(status); issues.flush(); return response(issue,actor);
    }
    private List<GroupMember> recipients(EmergencyIssue issue) {
        List<GroupMember> active=members.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(issue.getGroup().getId(),GroupMember.Status.ACTIVE);
        if(issue.getAudience()==EmergencyIssue.Audience.WHOLE_TEAM) return active;
        LinkedHashMap<Long,GroupMember> selected=new LinkedHashMap<>();
        if(issue.getProject().getLead()!=null) selected.put(issue.getProject().getLead().getId(),issue.getProject().getLead());
        selected.put(issue.getCreatedBy().getId(),issue.getCreatedBy());
        tasks.findAllByGroupIdOrderByCreatedAtDesc(issue.getGroup().getId()).stream()
                .filter(t->t.getProject()!=null&&t.getProject().getId().equals(issue.getProject().getId()))
                .forEach(t->{selected.put(t.getRequester().getId(),t.getRequester()); if(t.getAssignee()!=null) selected.put(t.getAssignee().getId(),t.getAssignee());});
        return selected.values().stream().filter(m->m.getStatus()==GroupMember.Status.ACTIVE).toList();
    }
    private void requireManage(EmergencyIssue issue,GroupMember actor) {
        boolean allowed=actor.getRole()==GroupMember.Role.LEADER||actor.getId().equals(issue.getCreatedBy().getId())
                ||issue.getProject().getLead()!=null&&actor.getId().equals(issue.getProject().getLead().getId());
        if(!allowed) throw new ApplicationException("EMERGENCY_ISSUE_FORBIDDEN",HttpStatus.FORBIDDEN,"긴급 이슈를 관리할 권한이 없습니다.");
    }
    private EmergencyIssue issue(Long id){return issues.findById(id).orElseThrow(()->new ApplicationException("EMERGENCY_ISSUE_NOT_FOUND",HttpStatus.NOT_FOUND,"긴급 이슈를 찾을 수 없습니다."));}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private Response response(EmergencyIssue i,GroupMember actor){boolean can=actor.getRole()==GroupMember.Role.LEADER||actor.getId().equals(i.getCreatedBy().getId())||i.getProject().getLead()!=null&&actor.getId().equals(i.getProject().getLead().getId()); return new Response(i.getId(),i.getGroup().getId(),i.getProject().getId(),i.getProject().getName(),i.getCreatedBy().getId(),i.getCreatedBy().getUser().getNickname(),i.getTitle(),i.getDescription(),i.getAudience().name(),i.getStatus().name(),i.getImageUrl(),i.getResolvedAt(),i.getCreatedAt(),i.getUpdatedAt(),i.getVersion(),can);}
}
