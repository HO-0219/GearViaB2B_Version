package com.teamproject.task.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TaskAssigneeChangeRequestRepository extends JpaRepository<TaskAssigneeChangeRequest, Long> {
    boolean existsByTaskIdAndStatus(Long taskId, TaskAssigneeChangeRequest.Status status);
    List<TaskAssigneeChangeRequest> findAllByTask_Group_IdOrderByCreatedAtDesc(Long groupId);
}
