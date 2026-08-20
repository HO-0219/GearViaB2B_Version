package com.teamproject.project.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectIssueImageRepository extends JpaRepository<ProjectIssueImage, Long> {
    List<ProjectIssueImage> findAllByIssueIdOrderBySortOrderAscIdAsc(Long issueId);
    List<ProjectIssueImage> findAllByIssueIdInOrderByIssueIdAscSortOrderAscIdAsc(List<Long> issueIds);
    boolean existsByIssueIdAndChecksumSha256(Long issueId, String checksumSha256);
    int countByIssueId(Long issueId);
    @Query("select coalesce(sum(i.sizeBytes), 0) from ProjectIssueImage i where i.issue.project.group.id = :groupId")
    long sumActiveBytesByGroupId(@Param("groupId") Long groupId);
}
