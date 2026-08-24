package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.AdminTaskResponse;
import com.teamproject.admin.application.dto.AdminDtos.PageResponse;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

/** Governance actions for tasks: force-hold ("suspend"), soft delete regardless of status, and undelete. */
@Service
public class AdminTaskService {
    private static final Set<Task.Status> TERMINAL = Set.of(
            Task.Status.COMPLETED, Task.Status.REJECTED, Task.Status.CANCELLED);

    private final TaskRepository tasks;

    public AdminTaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTaskResponse> list(int page, int size) {
        var result = tasks.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return new PageResponse<>(result.map(this::response).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<AdminTaskResponse> recentlyDeleted() {
        return tasks.findRecentlyDeleted().stream().map(this::response).toList();
    }

    @Transactional
    public AdminTaskResponse suspend(Long taskId, String reason) {
        Task task = required(taskId);
        if (task.getStatus() == Task.Status.ON_HOLD) {
            throw new ApplicationException("TASK_ALREADY_SUSPENDED", HttpStatus.CONFLICT, "이미 보류(정지) 상태인 업무입니다.");
        }
        if (TERMINAL.contains(task.getStatus())) {
            throw new ApplicationException("TASK_SUSPEND_STATE_INVALID", HttpStatus.CONFLICT,
                    "완료·반려·취소된 업무는 정지할 수 없습니다.");
        }
        task.hold(reason, Task.BlockerType.OTHER, Task.BlockerNextActionType.OTHER, null);
        return response(task);
    }

    @Transactional
    public AdminTaskResponse resume(Long taskId) {
        Task task = required(taskId);
        if (task.getStatus() != Task.Status.ON_HOLD) {
            throw new ApplicationException("TASK_NOT_SUSPENDED", HttpStatus.CONFLICT, "보류(정지) 상태인 업무만 재개할 수 있습니다.");
        }
        task.resume();
        return response(task);
    }

    @Transactional
    public void delete(Long taskId) {
        required(taskId).delete();
    }

    @Transactional
    public AdminTaskResponse restore(Long taskId) {
        Task task = tasks.findByIdIncludingDeleted(taskId)
                .filter(value -> value.getDeletedAt() != null)
                .orElseThrow(() -> new ApplicationException("TASK_NOT_DELETED", HttpStatus.CONFLICT,
                        "삭제된 업무만 복구할 수 있습니다."));
        task.restore();
        return response(task);
    }

    private Task required(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new ApplicationException(
                "TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
    }

    private AdminTaskResponse response(Task value) {
        var assignee = value.getAssignee();
        return new AdminTaskResponse(value.getId(), value.getGroup().getId(), value.getGroup().getName(),
                value.getTitle(), value.getStatus().name(), value.getRequester().getUser().getId(),
                value.getRequester().getUser().getNickname(),
                assignee == null ? null : assignee.getUser().getId(),
                assignee == null ? null : assignee.getUser().getNickname(),
                value.getDueAt(), value.getHoldReason(), value.getDeletedAt(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
