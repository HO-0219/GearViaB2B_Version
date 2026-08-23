package com.teamproject.task;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @SQLRestriction("deleted_at IS NULL")} on Task hides soft-deleted rows from every JPQL/derived
 * query. The admin restore flow needs a native-query escape hatch — this pins down that it actually works.
 */
@DataJpaTest
class TaskSoftDeleteQueryTest {
    @Autowired private UserRepository users;
    @Autowired private GroupRepository groups;
    @Autowired private GroupMemberRepository members;
    @Autowired private TaskRepository tasks;
    @Autowired private TestEntityManager entityManager;

    @Test
    void findByIdIncludingDeletedSeesATaskThatFindByIdHides() {
        User creator = users.save(new User("leader", "leader@example.com", "hash", "Leader", true));
        Group group = groups.save(Group.team("Team", null, "Asia/Seoul", creator));
        GroupMember requester = members.save(GroupMember.leader(group, creator));
        Task task = tasks.save(new Task(group, requester, "제목", "설명", Task.Priority.NORMAL, null));
        task.delete();
        tasks.saveAndFlush(task);
        // Detach everything so the next reads hit the DB instead of returning the still-managed
        // instance from this transaction's first-level cache, which would bypass @SQLRestriction too.
        entityManager.clear();

        assertThat(tasks.findById(task.getId())).isEmpty();
        Optional<Task> found = tasks.findByIdIncludingDeleted(task.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getDeletedAt()).isNotNull();
        assertThat(tasks.findRecentlyDeleted()).extracting(Task::getId).contains(task.getId());
    }
}
