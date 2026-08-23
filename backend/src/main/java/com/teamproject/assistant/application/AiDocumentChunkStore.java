package com.teamproject.assistant.application;

import com.teamproject.assistant.domain.AiDocumentChunk;
import com.teamproject.assistant.domain.AiDocumentChunkRepository;
import com.teamproject.assistant.domain.AiDocumentSource;
import com.teamproject.project.domain.ProjectDocument;
import com.teamproject.project.domain.ProjectDocumentRepository;
import com.teamproject.resource.domain.GroupResource;
import com.teamproject.resource.domain.GroupResourceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 색인의 DB 접근만 모아 둔 곳.
 *
 * <p>{@link AiDocumentIndexService} 가 임베딩 호출을 트랜잭션 밖에서 하려면 읽기와 쓰기가
 * 각각 짧은 트랜잭션이어야 한다. 같은 빈 안에서 @Transactional 메서드를 부르면 프록시를 타지
 * 않으므로 별도 빈으로 나눈다.
 *
 * <p>색인 대상은 두 저장소(그룹 자료실 {@link GroupResource}, 프로젝트 파일 {@link ProjectDocument})다.
 * 두 저장소의 ID 공간이 겹칠 수 있어({@code GroupResource#5}와 {@code ProjectDocument#5}가 동시에
 * 존재 가능) 어느 메서드를 부르든 {@link AiDocumentSource}를 항상 같이 넘긴다.
 */
@Service
public class AiDocumentChunkStore {
    private final AiDocumentChunkRepository chunks;
    private final GroupResourceRepository groupResources;
    private final ProjectDocumentRepository projectDocuments;

    public AiDocumentChunkStore(AiDocumentChunkRepository chunks, GroupResourceRepository groupResources,
            ProjectDocumentRepository projectDocuments) {
        this.chunks = chunks;
        this.groupResources = groupResources;
        this.projectDocuments = projectDocuments;
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(AiDocumentSource source, Long groupId) {
        return switch (source) {
            case GROUP_RESOURCE -> groupResources.findAllByGroupIdAndTaskIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(groupId)
                    .stream().map(resource -> new Candidate(resource.getId(), resource.getTitle(),
                            resource.getOriginalFilename(), resource.getStorageKey())).toList();
            case PROJECT_DOCUMENT -> projectDocuments.findAllByProjectGroupIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(groupId)
                    .stream().map(document -> new Candidate(document.getId(), document.getTitle(),
                            document.getOriginalFilename(), document.getStorageKey())).toList();
        };
    }

    @Transactional(readOnly = true)
    public Set<Long> allIndexedResourceIds(AiDocumentSource source, Long groupId) {
        return Set.copyOf(source == AiDocumentSource.GROUP_RESOURCE
                ? chunks.findIndexedGroupResourceIds(groupId)
                : chunks.findIndexedProjectDocumentIds(groupId));
    }

    /** 현재 임베딩 모델로 색인된 자료만 돌려준다. 모델을 바꾸면 옛 모델로 남은 자료는
     * 여기서 빠져서 reindex() 가 재색인 대상으로 다시 잡는다. */
    @Transactional(readOnly = true)
    public Set<Long> upToDateResourceIds(AiDocumentSource source, Long groupId, String modelId) {
        return Set.copyOf(source == AiDocumentSource.GROUP_RESOURCE
                ? chunks.findUpToDateGroupResourceIds(groupId, modelId)
                : chunks.findUpToDateProjectDocumentIds(groupId, modelId));
    }

    @Transactional(readOnly = true)
    public boolean isIndexed(AiDocumentSource source, Long resourceId) {
        return source == AiDocumentSource.GROUP_RESOURCE
                ? chunks.existsByGroupResourceId(resourceId)
                : chunks.existsByProjectDocumentId(resourceId);
    }

    /** 업로드 직후 자동색인 경로가 쓴다. 그룹 자료는 task 가 null인 것만, 프로젝트 파일은
     * 같은 그룹의 프로젝트에 속한 것만 검색 대상이라 그것만 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<Candidate> candidate(AiDocumentSource source, Long groupId, Long resourceId) {
        if (source == AiDocumentSource.GROUP_RESOURCE) {
            return groupResources.findByIdAndDeletedAtIsNull(resourceId)
                    .filter(resource -> resource.getGroup().getId().equals(groupId) && resource.getTask() == null)
                    .map(resource -> new Candidate(resource.getId(), resource.getTitle(),
                            resource.getOriginalFilename(), resource.getStorageKey()));
        }
        return projectDocuments.findByIdAndProjectGroupIdAndDeletedAtIsNull(resourceId, groupId)
                .map(document -> new Candidate(document.getId(), document.getTitle(),
                        document.getOriginalFilename(), document.getStorageKey()));
    }

    @Transactional
    public void deleteResource(AiDocumentSource source, Long resourceId) {
        if (source == AiDocumentSource.GROUP_RESOURCE) chunks.deleteByGroupResourceId(resourceId);
        else chunks.deleteByProjectDocumentId(resourceId);
    }

    @Transactional
    public void save(AiDocumentSource source, Long groupId, Candidate candidate, List<String> pieces,
            List<float[]> vectors, String modelId) {
        if (source == AiDocumentSource.GROUP_RESOURCE) {
            GroupResource resource = groupResources.findByIdAndDeletedAtIsNull(candidate.resourceId()).orElse(null);
            // 색인 도중 자료가 지워졌을 수 있다. 그러면 저장할 근거가 없으므로 조용히 건너뛴다.
            if (resource == null || !resource.getGroup().getId().equals(groupId)) return;
            // 옛 모델로 남은 청크가 있으면 먼저 지운다 — 두 모델 벡터가 섞이면 코사인 비교가 무의미해진다.
            chunks.deleteByGroupResourceId(candidate.resourceId());
            List<AiDocumentChunk> rows = new ArrayList<>(pieces.size());
            for (int index = 0; index < pieces.size(); index++) {
                rows.add(AiDocumentChunk.ofGroupResource(resource.getGroup(), resource, index, candidate.title(),
                        candidate.filename(), pieces.get(index), vectors.get(index), modelId));
            }
            chunks.saveAll(rows);
            return;
        }
        ProjectDocument document = projectDocuments.findByIdAndProjectGroupIdAndDeletedAtIsNull(candidate.resourceId(), groupId)
                .orElse(null);
        if (document == null) return;
        chunks.deleteByProjectDocumentId(candidate.resourceId());
        List<AiDocumentChunk> rows = new ArrayList<>(pieces.size());
        for (int index = 0; index < pieces.size(); index++) {
            rows.add(AiDocumentChunk.ofProjectDocument(document.getProject().getGroup(), document, index,
                    candidate.title(), candidate.filename(), pieces.get(index), vectors.get(index), modelId));
        }
        chunks.saveAll(rows);
    }

    public record Candidate(Long resourceId, String title, String filename, String storageKey) {}
}
