package com.teamproject.assistant.application;

import com.teamproject.assistant.application.AiDocumentChunkStore.Candidate;
import com.teamproject.assistant.application.dto.AiAssistantDtos.IndexResponse;
import com.teamproject.assistant.application.port.EmbeddingGateway;
import com.teamproject.assistant.domain.AiDocumentSource;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.resource.storage.FileStorage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 그룹 자료·프로젝트 파일 증분 색인.
 *
 * <p>자료는 한 번 올리면 내용이 바뀌지 않는다(수정 API 가 없다). 그래서 자료 ID 만으로 색인
 * 여부를 판단한다. 목록에서 사라진 자료는 삭제된 것이므로 색인에서도 지운다. 같은 그룹에 몇 번을
 * 돌려도 결과가 같다.
 *
 * <p>임베딩 호출은 트랜잭션 밖에서 한다. 메타데이터를 짧은 트랜잭션으로 읽고, 파일 읽기와 외부
 * 호출을 마친 뒤, 저장만 다시 짧은 트랜잭션으로 처리한다({@link AiDocumentChunkStore}).
 *
 * <p>그룹 자료실({@link AiDocumentSource#GROUP_RESOURCE})과 프로젝트 파일
 * ({@link AiDocumentSource#PROJECT_DOCUMENT})은 별개 저장소라 두 출처를 각각 훑는다.
 */
@Service
public class AiDocumentIndexService {
    private static final Logger log = LoggerFactory.getLogger(AiDocumentIndexService.class);

    private final GroupAuthorization authorization;
    private final AiDocumentChunkStore store;
    private final FileStorage storage;
    private final DocumentTextExtractor extractor;
    private final TextChunker chunker;
    private final EmbeddingGateway embeddings;

    public AiDocumentIndexService(GroupAuthorization authorization, AiDocumentChunkStore store,
            FileStorage storage, DocumentTextExtractor extractor, TextChunker chunker,
            EmbeddingGateway embeddings) {
        this.authorization = authorization;
        this.store = store;
        this.storage = storage;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddings = embeddings;
    }

    public IndexResponse reindex(Long userId, Long groupId) {
        authorization.requireActiveMember(groupId, userId);
        return reindexGroup(groupId);
    }

    /**
     * 사용자 없이 그룹만으로 재색인한다. {@link AiDocumentAutoIndexRetryScheduler} 가 자동색인
     * 실패(네트워크 오류 등 일시적 문제) 를 주기적으로 다시 시도할 때 쓴다 — 실패한 자료는 이미
     * 청크가 없어 "색인 안 됨" 상태 그대로이므로, 이 메서드를 다시 부르면 자연히 재시도된다.
     */
    public IndexResponse reindexGroup(Long groupId) {
        String modelId = embeddings.modelId();
        Result total = new Result();
        for (AiDocumentSource source : AiDocumentSource.values()) {
            reindexSource(source, groupId, modelId, total);
        }
        return new IndexResponse(total.added, total.skipped, total.removed, total.unsupported, total.failures);
    }

    private void reindexSource(AiDocumentSource source, Long groupId, String modelId, Result total) {
        List<Candidate> candidates = store.candidates(source, groupId);
        // 모델이 바뀌기 전 자료를 지웠는지 판단하려면 모델과 무관하게 "색인된 적 있음"이 필요하다.
        Set<Long> everIndexed = store.allIndexedResourceIds(source, groupId);
        // 재색인을 건너뛸지는 지금 모델로 색인됐는지로만 판단한다 — 옛 모델로 남은 자료는 다시 잡는다.
        Set<Long> upToDate = store.upToDateResourceIds(source, groupId, modelId);
        Set<Long> live = new HashSet<>();
        candidates.forEach(candidate -> live.add(candidate.resourceId()));

        for (Long stale : everIndexed) {
            if (!live.contains(stale)) {
                store.deleteResource(source, stale);
                total.removed++;
            }
        }
        for (Candidate candidate : candidates) {
            if (upToDate.contains(candidate.resourceId())) {
                total.skipped++;
                continue;
            }
            if (candidate.storageKey() == null) {
                // 외부 링크는 본문을 가져오지 않는다. 제목만으로는 색인 가치가 없다.
                total.skipped++;
                continue;
            }
            if (!extractor.supports(candidate.filename())) {
                total.unsupported++;
                continue;
            }
            switch (tryIndex(source, groupId, candidate, modelId)) {
                case ADDED -> total.added++;
                case UNSUPPORTED -> total.unsupported++;
                case FAILED -> total.failures.add(candidate.title());
            }
        }
    }

    /**
     * 자료 하나를 업로드 직후 색인한다({@link AiDocumentAutoIndexListener}).
     * 사용자가 누르는 재색인 버튼과 달리 업무 결과를 UI 에 보고하지 않는 조용한 경로라, 이미 색인된
     * 자료거나 지원하지 않는 형식이면 그냥 건너뛴다.
     */
    public void indexResource(AiDocumentSource source, Long groupId, Long resourceId) {
        if (store.isIndexed(source, resourceId)) return;
        Candidate candidate = store.candidate(source, groupId, resourceId).orElse(null);
        if (candidate == null || candidate.storageKey() == null || !extractor.supports(candidate.filename())) {
            return;
        }
        tryIndex(source, groupId, candidate, embeddings.modelId());
    }

    private Outcome tryIndex(AiDocumentSource source, Long groupId, Candidate candidate, String modelId) {
        List<String> pieces;
        try {
            byte[] content = storage.get(candidate.storageKey()).content();
            pieces = chunker.split(extractor.extract(content, candidate.filename()));
        } catch (RuntimeException exception) {
            // 자료 하나가 실패해도 나머지는 계속 색인한다. 본문은 로그에 남기지 않는다.
            log.warn("자료 {} 본문 추출 실패: {}", candidate.resourceId(), exception.getClass().getSimpleName());
            return Outcome.FAILED;
        }
        if (pieces.isEmpty()) return Outcome.UNSUPPORTED;
        try {
            store.save(source, groupId, candidate, pieces, embeddings.embed(pieces), modelId);
            return Outcome.ADDED;
        } catch (RuntimeException exception) {
            log.warn("자료 {} 색인 실패: {}", candidate.resourceId(), exception.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    private enum Outcome { ADDED, UNSUPPORTED, FAILED }

    private static final class Result {
        int added; int skipped; int removed; int unsupported;
        final List<String> failures = new ArrayList<>();
    }
}
