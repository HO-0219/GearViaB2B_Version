package com.teamproject.resource.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupResourceRepository extends JpaRepository<GroupResource, Long> {
    List<GroupResource> findAllByGroupIdAndTaskIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long groupId);
    List<GroupResource> findAllByTaskIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long taskId);
    Optional<GroupResource> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByGroupIdAndTaskIdAndChecksumSha256AndDeletedAtIsNull(Long groupId, Long taskId, String checksum);
    @Query("select coalesce(sum(r.sizeBytes), 0) from GroupResource r where r.group.id = :groupId and r.deletedAt is null and r.sizeBytes is not null")
    long sumActiveFileBytesByGroupId(@Param("groupId") Long groupId);
}
