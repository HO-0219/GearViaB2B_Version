package com.teamproject.admin.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminTaskServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final AdminTaskService service = new AdminTaskService(tasks);

    private Task newTask(Task.Status status) {
        User creator = new User("leader", "leader@example.com", "hash", "Leader", true);
        Group group = Group.team("Team", null, "Asia/Seoul", creator);
        GroupMember requester = GroupMember.leader(group, creator);
        Task task = new Task(group, requester, "제목", "설명", Task.Priority.NORMAL, null);
        forceStatus(task, status);
        return task;
    }

    /** Task's constructor always starts at REQUESTED/TODO; tests need every other status directly. */
    private void forceStatus(Task task, Task.Status status) {
        switch (status) {
            case IN_PROGRESS -> task.start();
            case ON_HOLD -> { task.start(); task.hold("사유", Task.BlockerType.OTHER, Task.BlockerNextActionType.OTHER, null); }
            case COMPLETED -> { task.start(); task.complete(); }
            case REJECTED -> task.reject(task.getRequester(), "반려");
            case CANCELLED -> task.cancel("취소");
            default -> { }
        }
    }

    @Test
    void suspendMovesAnActiveTaskToOnHoldWithTheGivenReason() {
        Task task = newTask(Task.Status.IN_PROGRESS);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));

        var response = service.suspend(1L, "정책 위반 검토");

        assertThat(response.status()).isEqualTo("ON_HOLD");
        assertThat(response.holdReason()).isEqualTo("정책 위반 검토");
    }

    @Test
    void suspendRejectsATaskThatIsAlreadyOnHold() {
        Task task = newTask(Task.Status.ON_HOLD);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.suspend(1L, "사유"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("이미 보류");
    }

    @Test
    void suspendRejectsATerminalTask() {
        Task task = newTask(Task.Status.COMPLETED);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.suspend(1L, "사유"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("완료·반려·취소");
    }

    @Test
    void resumeMovesAnOnHoldTaskBackToInProgress() {
        Task task = newTask(Task.Status.ON_HOLD);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));

        var response = service.resume(1L);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void resumeRejectsATaskThatIsNotOnHold() {
        Task task = newTask(Task.Status.IN_PROGRESS);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.resume(1L)).isInstanceOf(ApplicationException.class);
    }

    @Test
    void deleteSoftDeletesRegardlessOfStatus() {
        Task task = newTask(Task.Status.IN_PROGRESS);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));

        service.delete(1L);

        assertThat(task.getDeletedAt()).isNotNull();
    }

    @Test
    void restoreClearsDeletedAtOnASoftDeletedTask() {
        Task task = newTask(Task.Status.COMPLETED);
        task.delete();
        when(tasks.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(task));

        var response = service.restore(1L);

        assertThat(task.getDeletedAt()).isNull();
        assertThat(response.deletedAt()).isNull();
    }

    @Test
    void restoreRejectsATaskThatIsNotDeleted() {
        Task task = newTask(Task.Status.IN_PROGRESS);
        when(tasks.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.restore(1L)).isInstanceOf(ApplicationException.class);
    }

    @Test
    void operationsOnAnUnknownTaskThrowNotFound() {
        when(tasks.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(99L, "사유")).isInstanceOf(ApplicationException.class);
    }
}
