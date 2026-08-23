package com.teamproject.assistant.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.project.domain.ProjectDocument;
import com.teamproject.resource.domain.GroupResource;
import jakarta.persistence.*;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;

/**
 * 그룹 자료 또는 프로젝트 파일 본문의 한 조각과 그 임베딩.
 *
 * <p>임베딩은 float 배열을 big-endian 으로 편 BLOB 이다. MySQL 8.4 에는 VECTOR 타입이 없고
 * 코퍼스 규모가 작아(그룹당 수십 청크) 검색은 그룹 단위로 전부 읽어 메모리에서 코사인을 잰다.
 *
 * <p>출처는 {@code groupResource}와 {@code projectDocument} 중 정확히 하나만 채워진다
 * (DB에 {@code ck_ai_document_chunks_single_source} 체크 제약으로도 강제된다). 두 저장소가
 * 서로 다른 테이블이라 하나의 FK로 묶을 수 없어 이렇게 나눴다.
 */
@Entity
@Table(name = "ai_document_chunks")
public class AiDocumentChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_resource_id") private GroupResource groupResource;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_document_id") private ProjectDocument projectDocument;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    @Column(nullable = false, length = 120) private String title;
    @Column(length = 255) private String filename;
    // @Lob 만 붙이면 Hibernate 가 tinytext/tinyblob 을 기대해 마이그레이션과 어긋난다(validate 실패).
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false, columnDefinition = "longblob") private byte[] embedding;
    @Column(nullable = false) private int dimensions;
    @Column(name = "embedding_model", nullable = false, length = 120) private String embeddingModel;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    protected AiDocumentChunk() {}

    private AiDocumentChunk(Group group, int chunkIndex, String title, String filename, String content,
            float[] embedding, String embeddingModel) {
        this.group = group;
        this.chunkIndex = chunkIndex;
        this.title = title;
        this.filename = filename;
        this.content = content;
        this.embedding = pack(embedding);
        this.dimensions = embedding.length;
        this.embeddingModel = embeddingModel;
        this.createdAt = LocalDateTime.now();
    }

    public static AiDocumentChunk ofGroupResource(Group group, GroupResource resource, int chunkIndex,
            String title, String filename, String content, float[] embedding, String embeddingModel) {
        AiDocumentChunk chunk = new AiDocumentChunk(group, chunkIndex, title, filename, content, embedding, embeddingModel);
        chunk.groupResource = resource;
        return chunk;
    }

    public static AiDocumentChunk ofProjectDocument(Group group, ProjectDocument document, int chunkIndex,
            String title, String filename, String content, float[] embedding, String embeddingModel) {
        AiDocumentChunk chunk = new AiDocumentChunk(group, chunkIndex, title, filename, content, embedding, embeddingModel);
        chunk.projectDocument = document;
        return chunk;
    }

    public float[] vector() {
        ByteBuffer buffer = ByteBuffer.wrap(embedding);
        float[] values = new float[dimensions];
        for (int index = 0; index < dimensions; index++) values[index] = buffer.getFloat();
        return values;
    }

    private static byte[] pack(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES);
        for (float value : values) buffer.putFloat(value);
        return buffer.array();
    }

    public Long getId() { return id; }
    public int getChunkIndex() { return chunkIndex; }
    public String getTitle() { return title; }
    public String getFilename() { return filename; }
    public String getContent() { return content; }
    public int getDimensions() { return dimensions; }
    public String getEmbeddingModel() { return embeddingModel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
