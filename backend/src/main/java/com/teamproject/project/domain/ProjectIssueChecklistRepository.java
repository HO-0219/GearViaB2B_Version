package com.teamproject.project.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectIssueChecklistRepository extends JpaRepository<ProjectIssueChecklistItem, Long> {
    List<ProjectIssueChecklistItem> findAllByIssueIdOrderBySortOrderAscIdAsc(Long issueId);
    List<ProjectIssueChecklistItem> findAllByIssueIdInOrderByIssueIdAscSortOrderAscIdAsc(List<Long> issueIds);
    int countByIssueId(Long issueId);
}
