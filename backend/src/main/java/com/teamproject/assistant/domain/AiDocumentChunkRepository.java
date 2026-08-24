package com.teamproject.assistant.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiDocumentChunkRepository extends JpaRepository<AiDocumentChunk, Long> {
    // id DESC로 최신 청크부터 유지한다 — 그룹이 MAX_SCANNED_CHUNKS를 넘으면 잘리는 건
    // 뒷부분(오래된 청크)이어야, 최근에 올린 문서가 검색 후보에서 빠지지 않는다.
    // 출처(group_resource/project_document)가 지워졌으면 그 즉시 검색에서 빠져야 하므로
    // 두 연관을 각각 left join 해 살아있는 쪽만 남긴다.
    @Query("select chunk from AiDocumentChunk chunk"
            + " left join chunk.groupResource gr left join chunk.projectDocument pd"
            + " where chunk.group.id = :groupId"
            + " and ((gr is not null and gr.deletedAt is null) or (pd is not null and pd.deletedAt is null))"
            + " order by chunk.id desc")
    List<AiDocumentChunk> findLiveByGroupIdOrderByIdDesc(@Param("groupId") Long groupId, Pageable pageable);

    @Query("select distinct chunk.groupResource.id from AiDocumentChunk chunk"
            + " where chunk.group.id = :groupId and chunk.groupResource is not null")
    List<Long> findIndexedGroupResourceIds(@Param("groupId") Long groupId);

    @Query("select distinct chunk.projectDocument.id from AiDocumentChunk chunk"
            + " where chunk.group.id = :groupId and chunk.projectDocument is not null")
    List<Long> findIndexedProjectDocumentIds(@Param("groupId") Long groupId);

    @Query("select distinct chunk.groupResource.id from AiDocumentChunk chunk"
            + " where chunk.group.id = :groupId and chunk.groupResource is not null and chunk.embeddingModel = :modelId")
    List<Long> findUpToDateGroupResourceIds(@Param("groupId") Long groupId, @Param("modelId") String modelId);

    @Query("select distinct chunk.projectDocument.id from AiDocumentChunk chunk"
            + " where chunk.group.id = :groupId and chunk.projectDocument is not null and chunk.embeddingModel = :modelId")
    List<Long> findUpToDateProjectDocumentIds(@Param("groupId") Long groupId, @Param("modelId") String modelId);

    boolean existsByGroupResourceId(Long groupResourceId);
    boolean existsByProjectDocumentId(Long projectDocumentId);

    void deleteByGroupResourceId(Long groupResourceId);
    void deleteByProjectDocumentId(Long projectDocumentId);
}
