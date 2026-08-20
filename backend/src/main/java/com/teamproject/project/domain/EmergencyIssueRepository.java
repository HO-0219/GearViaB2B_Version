package com.teamproject.project.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmergencyIssueRepository extends JpaRepository<EmergencyIssue, Long> {
    List<EmergencyIssue> findAllByGroupIdOrderByStatusAscCreatedAtDesc(Long groupId);
}
