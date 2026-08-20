package com.teamproject.project.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectIssueRepository extends JpaRepository<ProjectIssue, Long> {
    List<ProjectIssue> findAllByProjectIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(Long projectId);
    List<ProjectIssue> findAllByProjectIdOrderBySortOrderAscIdAsc(Long projectId);
    List<ProjectIssue> findAllByParentIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(Long parentId);
    Optional<ProjectIssue> findByIdAndArchivedAtIsNull(Long id);
    int countByProjectIdAndParentIdAndArchivedAtIsNull(Long projectId, Long parentId);
    int countByProjectIdAndParentIsNullAndArchivedAtIsNull(Long projectId);
    int countByProjectIdAndArchivedAtIsNull(Long projectId);
}
