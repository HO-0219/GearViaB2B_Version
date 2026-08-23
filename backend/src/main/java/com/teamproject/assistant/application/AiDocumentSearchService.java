package com.teamproject.assistant.application;

import com.teamproject.assistant.application.port.EmbeddingGateway;
import com.teamproject.assistant.domain.AiDocumentChunk;
import com.teamproject.assistant.domain.AiDocumentChunkRepository;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 그룹 자료 검색.
 *
 * <p>groupId 는 호출자가 인증으로 확인한 값이어야 한다. 이 값을 LLM 이 고를 수 있으면
 * 그게 곧 다른 팀 자료로 가는 통로다. 그래서 검색 도구 인자에 groupId 를 두지 않고
 * 현재 작업공간을 서버가 넣는다.
 */
@Service
public class AiDocumentSearchService {
    private static final Logger log = LoggerFactory.getLogger(AiDocumentSearchService.class);
    /** 그룹당 청크가 수십 개뿐이라 5로는 근거 문서가 밀려난다. 8이면 회수가 올라가고 비용은 그대로다. */
    public static final int DEFAULT_LIMIT = 8;
    /** 청크를 전부 읽어 메모리에서 재기 때문에 그룹당 상한을 둔다(24KB 코퍼스 기준 수십 개). */
    private static final int MAX_SCANNED_CHUNKS = 5000;

    private final AiDocumentChunkRepository chunks;
    private final EmbeddingGateway embeddings;

    public AiDocumentSearchService(AiDocumentChunkRepository chunks, EmbeddingGateway embeddings) {
        this.chunks = chunks;
        this.embeddings = embeddings;
    }

    public List<Passage> search(Long groupId, String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        List<Scored> scored = scored(groupId);
        if (scored.isEmpty()) return List.of();
        float[] vector = embeddings.embed(List.of(query)).get(0);
        List<Scored> comparable = scored.stream()
                .filter(candidate -> candidate.vector().length == vector.length)
                .toList();
        if (comparable.size() < scored.size()) {
            log.warn("group {}: {} chunk(s) skipped, embedding dimension mismatch"
                    + " (query {} vs stored) — reindex required after an embedding model change",
                    groupId, scored.size() - comparable.size(), vector.length);
        }
        return comparable.stream()
                .map(candidate -> new Passage(candidate.title(), candidate.filename(),
                        round(cosine(vector, candidate.vector())), candidate.content()))
                .sorted(Comparator.comparingDouble(Passage::score).reversed())
                .limit(Math.max(1, Math.min(limit, 12)))
                .toList();
    }

    /**
     * 임베딩 호출 전에 후보를 다 읽어 둔다. 외부 호출이 트랜잭션을 잡고 있지 않게 하려는 것이다.
     * 그래서 이 메서드에도 search() 에도 @Transactional 을 걸지 않는다. 상한은 쿼리 자체에
     * Pageable 로 걸어서, DB 에서 그룹 전체를 다 읽어온 뒤에 자르는 일이 없게 한다.
     */
    private List<Scored> scored(Long groupId) {
        List<AiDocumentChunk> stored = chunks.findLiveByGroupIdOrderByIdDesc(groupId,
                PageRequest.of(0, MAX_SCANNED_CHUNKS));
        return stored.stream()
                .map(chunk -> new Scored(chunk.getTitle(), chunk.getFilename(), chunk.getContent(),
                        chunk.vector()))
                .toList();
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    private record Scored(String title, String filename, String content, float[] vector) {}

    public record Passage(String title, String filename, double score, String quotedText) {}
}
