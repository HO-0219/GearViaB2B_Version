package com.teamproject.task.application.dto;

import com.teamproject.task.domain.Task;

public record TaskListFilter(String query, Task.Status status, Task.Priority priority, Long projectId,
        Assignment assignment, Due due) {
    public enum Assignment { ALL, MINE, UNASSIGNED }
    public enum Due { ALL, OVERDUE, DUE_SOON }
    public TaskListFilter {
        assignment = assignment == null ? Assignment.ALL : assignment;
        due = due == null ? Due.ALL : due;
    }
}
