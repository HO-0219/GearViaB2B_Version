package com.teamproject.task.infrastructure;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.task.application.dto.TaskListFilter;
import com.teamproject.task.domain.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class TaskListQueryRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public List<Task> find(Long groupId, Long userId, TaskListFilter filter, LocalDateTime now, int limit) {
        if (limit < 1 || limit > 5000) {
            throw new ApplicationException("TASK_QUERY_LIMIT_INVALID", HttpStatus.INTERNAL_SERVER_ERROR,
                    "업무 조회 제한 설정이 올바르지 않습니다.");
        }
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Task> query = builder.createQuery(Task.class);
        Root<Task> task = query.from(Task.class);
        fetchResponseGraph(task);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(task.get("group").get("id"), groupId));
        addTextPredicate(builder, task, filter.query(), predicates);
        if (filter.status() != null) {
            predicates.add(builder.equal(task.get("status"), filter.status()));
        }
        if (filter.priority() != null) {
            predicates.add(builder.equal(task.get("priority"), filter.priority()));
        }
        if (filter.projectId() != null) {
            predicates.add(builder.equal(task.get("project").get("id"), filter.projectId()));
        }
        addAssignmentPredicate(builder, task, userId, filter.assignment(), predicates);
        addDuePredicate(builder, task, filter.due(), now, predicates);

        query.select(task).distinct(true)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.desc(task.get("createdAt")), builder.desc(task.get("id")));
        List<Task> results = entityManager.createQuery(query).setMaxResults(limit + 1).getResultList();
        if (results.size() > limit) {
            throw new ApplicationException("TASK_QUERY_LIMIT_EXCEEDED", HttpStatus.BAD_REQUEST,
                    "조회 결과가 너무 많습니다. 검색어나 필터를 추가해 주세요.");
        }
        return results;
    }

    private void fetchResponseGraph(Root<Task> task) {
        task.fetch("group", JoinType.INNER);
        task.fetch("requester", JoinType.INNER).fetch("user", JoinType.INNER);
        task.fetch("approver", JoinType.LEFT).fetch("user", JoinType.LEFT);
        task.fetch("assignee", JoinType.LEFT).fetch("user", JoinType.LEFT);
        task.fetch("project", JoinType.LEFT);
        task.fetch("projectTopic", JoinType.LEFT);
    }

    private void addTextPredicate(CriteriaBuilder builder, Root<Task> task, String input,
            List<Predicate> predicates) {
        if (input == null || input.isBlank()) {
            return;
        }
        String pattern = "%" + escapeLike(input.trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(builder.or(
                builder.like(builder.lower(task.get("title")), pattern, '\\'),
                builder.like(builder.lower(task.get("description")), pattern, '\\')));
    }

    private void addAssignmentPredicate(CriteriaBuilder builder, Root<Task> task, Long userId,
            TaskListFilter.Assignment assignment, List<Predicate> predicates) {
        if (assignment == TaskListFilter.Assignment.UNASSIGNED) {
            predicates.add(builder.isNull(task.get("assignee")));
        } else if (assignment == TaskListFilter.Assignment.MINE) {
            Join<Object, Object> assignee = task.join("assignee", JoinType.LEFT);
            predicates.add(builder.equal(assignee.get("user").get("id"), userId));
        }
    }

    private void addDuePredicate(CriteriaBuilder builder, Root<Task> task, TaskListFilter.Due due,
            LocalDateTime now, List<Predicate> predicates) {
        if (due == TaskListFilter.Due.ALL) {
            return;
        }
        predicates.add(builder.isNotNull(task.get("dueAt")));
        predicates.add(builder.not(task.get("status").in(
                Task.Status.COMPLETED, Task.Status.REJECTED, Task.Status.CANCELLED)));
        if (due == TaskListFilter.Due.OVERDUE) {
            predicates.add(builder.lessThan(task.get("dueAt"), now));
        } else {
            predicates.add(builder.greaterThanOrEqualTo(task.get("dueAt"), now));
            predicates.add(builder.lessThanOrEqualTo(task.get("dueAt"), now.plusDays(7)));
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
