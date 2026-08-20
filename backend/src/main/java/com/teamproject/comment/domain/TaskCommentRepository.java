package com.teamproject.comment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findAllByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);
}
