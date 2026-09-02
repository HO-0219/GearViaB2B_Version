package com.teamproject.task;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.task.application.dto.TaskListFilter;
import com.teamproject.task.application.dto.TaskListFilter.Assignment;
import com.teamproject.task.application.dto.TaskListFilter.Due;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.infrastructure.TaskListQueryRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TaskListQueryRepository.class)
class TaskListQueryRepositoryTest {
    @Autowired TaskListQueryRepository queries;
    @Autowired TaskRepository tasks;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    private User user;
    private Group group;
    private GroupMember member;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        user = users.save(new User("query_user", "query_user@example.com", "hash", "Query User", true));
        group = groups.save(Group.personal(user));
        member = members.save(GroupMember.leader(group, user));
        now = LocalDateTime.now();
    }

    @Test
    void filtersBeforeMaterializingEntities() {
        Task match = new Task(group, member, "release gate", "production release", Task.Priority.HIGH,
                now.minusHours(2));
        match.start();
        tasks.save(match);
        tasks.save(new Task(group, member, "release notes", null, Task.Priority.HIGH, now.plusDays(1)));
        tasks.save(new Task(group, member, "unrelated", null, Task.Priority.NORMAL, now.minusHours(1)));
        TaskListFilter filter = new TaskListFilter("release", Task.Status.IN_PROGRESS,
                Task.Priority.HIGH, null, Assignment.MINE, Due.OVERDUE);

        assertThat(queries.find(group.getId(), user.getId(), filter, now, 100))
                .extracting(Task::getTitle)
                .containsExactly("release gate");
    }

    @Test
    void reportsThatTheResultMustBeNarrowed() {
        tasks.save(new Task(group, member, "one", null, Task.Priority.NORMAL, null));
        tasks.save(new Task(group, member, "two", null, Task.Priority.NORMAL, null));
        tasks.save(new Task(group, member, "three", null, Task.Priority.NORMAL, null));
        TaskListFilter empty = new TaskListFilter(null, null, null, null, null, null);

        assertThatThrownBy(() -> queries.find(group.getId(), user.getId(), empty, now, 2))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).code())
                        .isEqualTo("TASK_QUERY_LIMIT_EXCEEDED"));
    }
}
