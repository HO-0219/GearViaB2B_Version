package com.teamproject.task;

import com.teamproject.TeamProjectApplication;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.domain.TaskStatusHistory;
import com.teamproject.task.domain.TaskStatusHistoryRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TeamProjectApplication.class)
class TaskOptimisticLockIntegrationTest {
    @Autowired TransactionTemplate transaction;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired TaskStatusHistoryRepository histories;

    @Test
    void exactlyOneOfTwoConcurrentUpdatesSucceeds() throws Exception {
        Fixture fixture = createFixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch updateStart = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> concurrentAccept(fixture.taskId(), bothLoaded, updateStart));
            Future<String> second = executor.submit(() -> concurrentAccept(fixture.taskId(), bothLoaded, updateStart));
            assertThat(bothLoaded.await(5, TimeUnit.SECONDS)).isTrue();
            updateStart.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "VERSION_CONFLICT");
            transaction.executeWithoutResult(status -> {
                Task saved = tasks.findById(fixture.taskId()).orElseThrow();
                assertThat(saved.getStatus()).isEqualTo(Task.Status.TODO);
                assertThat(saved.getVersion()).isEqualTo(1);
                assertThat(histories.findAllByTaskIdOrderByCreatedAtAsc(fixture.taskId())).hasSize(2);
            });
        } finally {
            updateStart.countDown();
            executor.shutdownNow();
            cleanup(fixture);
        }
    }

    private String concurrentAccept(Long taskId, CountDownLatch bothLoaded, CountDownLatch updateStart) {
        try {
            transaction.executeWithoutResult(status -> {
                Task task = tasks.findById(taskId).orElseThrow();
                GroupMember actor = task.getRequester();
                bothLoaded.countDown();
                await(updateStart);
                task.accept(actor);
                tasks.flush();
                histories.save(new TaskStatusHistory(
                        task, Task.Status.REQUESTED, Task.Status.TODO, actor, null));
            });
            return "SUCCESS";
        } catch (RuntimeException exception) {
            if (isOptimisticConflict(exception)) return "VERSION_CONFLICT";
            throw exception;
        }
    }

    private Fixture createFixture() {
        String suffix = Long.toString(System.nanoTime(), 36);
        return transaction.execute(status -> {
            User user = users.save(new User("lock_" + suffix, "lock_" + suffix + "@example.com",
                    "test-hash", "동시성 사용자", true));
            Group group = groups.save(Group.team("동시성 테스트 팀", null, "Asia/Seoul", user));
            GroupMember member = members.save(GroupMember.leader(group, user));
            Task task = tasks.save(new Task(group, member, "동시 수정 업무", null, Task.Priority.NORMAL, null));
            histories.save(new TaskStatusHistory(task, null, Task.Status.REQUESTED, member, null));
            return new Fixture(user.getId(), group.getId(), member.getId(), task.getId());
        });
    }

    private void cleanup(Fixture fixture) {
        transaction.executeWithoutResult(status -> {
            histories.deleteAllInBatch(histories.findAllByTaskIdOrderByCreatedAtAsc(fixture.taskId()));
            tasks.deleteById(fixture.taskId());
            tasks.flush();
            members.deleteById(fixture.memberId());
            members.flush();
            groups.deleteById(fixture.groupId());
            groups.flush();
            users.deleteById(fixture.userId());
            users.flush();
        });
    }

    private boolean isOptimisticConflict(Throwable value) {
        for (Throwable current = value; current != null; current = current.getCause()) {
            if (current instanceof OptimisticLockException
                    || current instanceof OptimisticLockingFailureException) return true;
        }
        return false;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("동시 요청 대기 시간 초과");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 요청 대기 중 중단됨", exception);
        }
    }

    private record Fixture(Long userId, Long groupId, Long memberId, Long taskId) {}
}
