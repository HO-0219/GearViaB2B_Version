package com.teamproject.project.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    List<ProjectDocument> findAllByProjectIdAndIssueNodeIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
    List<ProjectDocument> findAllByProjectIdAndIssueNodeIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId, Long issueId);
    Optional<ProjectDocument> findByIdAndDeletedAtIsNull(Long id);
    // RAG 색인 대상 조회용 — 그룹의 모든 프로젝트 파일을 위치(issueNode) 구분 없이 훑는다.
    List<ProjectDocument> findAllByProjectGroupIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long groupId);
    Optional<ProjectDocument> findByIdAndProjectGroupIdAndDeletedAtIsNull(Long id, Long groupId);
    boolean existsByProjectIdAndIssueNodeIdAndChecksumSha256AndDeletedAtIsNull(Long projectId, Long issueId, String checksum);
    boolean existsByProjectIdAndIssueNodeIsNullAndChecksumSha256AndDeletedAtIsNull(Long projectId, String checksum);
    @Query("select coalesce(sum(d.sizeBytes), 0) from ProjectDocument d where d.project.group.id = :groupId and d.deletedAt is null and d.sizeBytes is not null")
    long sumActiveFileBytesByGroupId(@Param("groupId") Long groupId);
}
