package com.teamproject.project.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.project.application.dto.ProjectDtos.*;
import com.teamproject.project.domain.Project;
import com.teamproject.project.domain.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final GroupAuthorization authorization;

    public ProjectService(ProjectRepository projects, GroupAuthorization authorization) {
        this.projects = projects;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Long userId, Long groupId) {
        GroupMember viewer = requireTeamMember(groupId, userId);
        return projects.findAllByGroupIdOrderByUpdatedAtDescIdDesc(groupId).stream()
                .map(project -> response(project, viewer)).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long userId, Long projectId) {
        Project project = project(projectId);
        GroupMember viewer = authorization.requireActiveMember(project.getGroup().getId(), userId);
        return response(project, viewer);
    }

    @Transactional
    public ProjectResponse create(Long userId, Long groupId, CreateProjectRequest request) {
        GroupMember leader = requireTeamLeader(groupId, userId);
        validateDates(request.startDate(), request.dueDate());
        GroupMember lead = optionalMember(groupId, request.leadMemberId());
        Project saved = projects.save(new Project(leader.getGroup(), leader, lead,
                request.name().trim(), blankToNull(request.description()), request.startDate(), request.dueDate()));
        projects.flush();
        return response(saved, leader);
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, UpdateProjectRequest request) {
        Project project = project(projectId);
        GroupMember leader = requireTeamLeader(project.getGroup().getId(), userId);
        if (project.getVersion() != request.expectedVersion()) throw conflict();
        validateDates(request.startDate(), request.dueDate());
        Project.Status status = status(request.status());
        project.update(request.name().trim(), blankToNull(request.description()),
                optionalMember(project.getGroup().getId(), request.leadMemberId()), status,
                request.startDate(), request.dueDate());
        // Return the version clients must use for their next optimistic-lock request.
        projects.flush();
        return response(project, leader);
    }

    @Transactional
    public void archive(Long userId, Long projectId, long expectedVersion) {
        Project project = project(projectId);
        requireTeamLeader(project.getGroup().getId(), userId);
        if (project.getVersion() != expectedVersion) throw conflict();
        project.archive();
    }

    private GroupMember requireTeamMember(Long groupId, Long userId) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        if (member.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("TEAM_PROJECT_REQUIRED", HttpStatus.BAD_REQUEST,
                    "팀 그룹에서만 프로젝트를 사용할 수 있습니다.");
        }
        return member;
    }

    private GroupMember requireTeamLeader(Long groupId, Long userId) {
        GroupMember member = authorization.requireLeader(groupId, userId);
        if (member.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("TEAM_PROJECT_REQUIRED", HttpStatus.BAD_REQUEST,
                    "팀 그룹에서만 프로젝트를 사용할 수 있습니다.");
        }
        return member;
    }

    private GroupMember optionalMember(Long groupId, Long memberId) {
        return memberId == null ? null : authorization.requireActiveMemberById(groupId, memberId);
    }

    private Project project(Long id) {
        return projects.findById(id).orElseThrow(() -> new ApplicationException(
                "PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
    }

    private void validateDates(LocalDate start, LocalDate due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw new ApplicationException("PROJECT_DATE_INVALID", HttpStatus.BAD_REQUEST,
                    "프로젝트 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private Project.Status status(String value) {
        try { return Project.Status.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException exception) {
            throw new ApplicationException("PROJECT_STATUS_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 프로젝트 상태를 선택해 주세요.");
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ApplicationException conflict() { return new ApplicationException(
            "PROJECT_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "프로젝트가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요."); }

    private ProjectResponse response(Project value, GroupMember viewer) {
        return new ProjectResponse(value.getId(), value.getGroup().getId(), value.getName(), value.getDescription(),
                value.getStatus().name(), value.getLead() == null ? null : value.getLead().getId(),
                value.getLead() == null ? null : value.getLead().getUser().getNickname(),
                value.getCreatedBy().getId(), value.getCreatedBy().getUser().getNickname(),
                value.getStartDate(), value.getDueDate(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt(),
                viewer.getRole() == GroupMember.Role.LEADER,
                value.getStatus() != Project.Status.ARCHIVED && (viewer.getRole() == GroupMember.Role.LEADER
                        || value.getLead() != null && value.getLead().getId().equals(viewer.getId())));
    }
}
