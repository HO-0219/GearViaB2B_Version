package com.teamproject.assistant;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.assistant.application.AiDocumentIndexService;
import com.teamproject.assistant.application.AiDocumentSearchService;
import com.teamproject.assistant.application.port.EmbeddingGateway;
import com.teamproject.assistant.domain.AiDocumentSource;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.resource.application.ResourceService;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = B2BGearViaApplication.class,
        properties = "app.storage.local-root=target/test-uploads")
@Transactional
class AiDocumentRagTest {
    /** 어휘 3개의 등장 여부만 보는 결정적 임베딩. 실제 provider 없이 순위를 잴 수 있다. */
    private static final List<String> VOCABULARY = List.of("금요일", "배포", "휴가");

    @MockBean EmbeddingGateway embeddings;
    @Autowired AiDocumentIndexService indexService;
    @Autowired AiDocumentSearchService searchService;
    @Autowired ResourceService resources;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @BeforeEach
    void stubEmbeddings() {
        Mockito.when(embeddings.modelId()).thenReturn("test-embedding-model");
        Mockito.when(embeddings.embed(Mockito.anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(AiDocumentRagTest::vector).toList();
        });
    }

    @Test
    void indexesOnceAndSkipsTheSameResourceOnReindex() {
        Fixture fixture = fixture();
        upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다. 배포는 화요일과 목요일에만 한다.");

        var first = indexService.reindex(fixture.userId(), fixture.groupId());
        var second = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(first.indexed()).isEqualTo(1);
        assertThat(first.failures()).isEmpty();
        assertThat(second.indexed()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
    }

    @Test
    void countsUnreadableFormatsAsUnsupportedInsteadOfFailing() {
        Fixture fixture = fixture();
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        upload(fixture, "설계 이미지", "설계.png", png);

        var result = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(result.unsupported()).isEqualTo(1);
        assertThat(result.indexed()).isZero();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void countsAMalformedPdfAsAFailureNotUnsupported() {
        Fixture fixture = fixture();
        upload(fixture, "설계 문서", "설계.pdf", "%PDF-1.4 본문");

        var result = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(result.failures()).containsExactly("설계 문서");
        assertThat(result.indexed()).isZero();
        assertThat(result.unsupported()).isZero();
    }

    @Test
    void indexesASingleResourceWithoutWaitingForReindex() {
        Fixture fixture = fixture();
        Long resourceId = upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");

        indexService.indexResource(AiDocumentSource.GROUP_RESOURCE, fixture.groupId(), resourceId);

        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isNotEmpty();
        // 이미 색인됐으니 재색인 버튼을 눌러도 다시 세지 않는다.
        var result = indexService.reindex(fixture.userId(), fixture.groupId());
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.indexed()).isZero();
    }

    @Test
    void indexResourceSkipsUnsupportedFormatsSilently() {
        Fixture fixture = fixture();
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        Long resourceId = upload(fixture, "설계 이미지", "설계.png", png);

        indexService.indexResource(AiDocumentSource.GROUP_RESOURCE, fixture.groupId(), resourceId);

        var result = indexService.reindex(fixture.userId(), fixture.groupId());
        assertThat(result.unsupported()).isEqualTo(1);
    }

    @Test
    void ranksTheRelevantPassageFirst() {
        Fixture fixture = fixture();
        upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        upload(fixture, "휴가 규정", "휴가.txt", "휴가는 사흘 전까지 신청한다.");
        indexService.reindex(fixture.userId(), fixture.groupId());

        var passages = searchService.search(fixture.groupId(), "금요일 배포 규칙", 5);

        assertThat(passages).isNotEmpty();
        assertThat(passages.get(0).title()).isEqualTo("배포 절차서");
        assertThat(passages.get(0).quotedText()).contains("금요일");
    }

    @Test
    void neverReturnsAnotherGroupsDocuments() {
        Fixture owner = fixture();
        upload(owner, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        indexService.reindex(owner.userId(), owner.groupId());
        Fixture other = fixture();

        assertThat(searchService.search(other.groupId(), "금요일 배포 규칙", 5)).isEmpty();
    }

    @Test
    void removesChunksWhenTheResourceIsDeleted() {
        Fixture fixture = fixture();
        Long resourceId = upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        indexService.reindex(fixture.userId(), fixture.groupId());
        resources.delete(fixture.userId(), resourceId);

        var result = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(result.removed()).isEqualTo(1);
        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isEmpty();
    }

    @Test
    void excludesADeletedResourceFromSearchBeforeTheNextReindex() {
        Fixture fixture = fixture();
        Long resourceId = upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        indexService.reindex(fixture.userId(), fixture.groupId());
        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isNotEmpty();

        resources.delete(fixture.userId(), resourceId);

        // 재색인을 아직 안 돌렸어도 삭제된 자료의 청크는 검색에 나오면 안 된다.
        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isEmpty();
    }

    @Test
    void reindexesAResourceWhenTheEmbeddingModelChanges() {
        Fixture fixture = fixture();
        upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        indexService.reindex(fixture.userId(), fixture.groupId());
        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isNotEmpty();

        Mockito.when(embeddings.modelId()).thenReturn("new-embedding-model");

        var result = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        // 새 모델로도 여전히 검색된다 — 옛 벡터가 섞여 남아 있지 않다는 뜻이다.
        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isNotEmpty();
        var third = indexService.reindex(fixture.userId(), fixture.groupId());
        assertThat(third.skipped()).isEqualTo(1);
        assertThat(third.indexed()).isZero();
    }

    private static float[] vector(String text) {
        float[] values = new float[VOCABULARY.size()];
        for (int index = 0; index < VOCABULARY.size(); index++) {
            values[index] = text.contains(VOCABULARY.get(index)) ? 1f : 0f;
        }
        // 전부 0인 벡터는 코사인이 정의되지 않으므로 작은 상수를 깔아 둔다.
        for (int index = 0; index < values.length; index++) values[index] += 0.01f;
        return values;
    }

    private Long upload(Fixture fixture, String title, String filename, String content) {
        return upload(fixture, title, filename, content.getBytes(StandardCharsets.UTF_8));
    }

    private Long upload(Fixture fixture, String title, String filename, byte[] content) {
        return resources.upload(fixture.userId(), fixture.groupId(), null, title,
                new MockMultipartFile("file", filename, "text/plain", content)).id();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        User user = users.save(new User("rag_" + suffix, "rag_" + suffix + "@example.com",
                "hash", "RAG 사용자", true));
        Group group = groups.save(Group.team("RAG 테스트 그룹", null, "Asia/Seoul", user));
        members.save(GroupMember.leader(group, user));
        return new Fixture(user.getId(), group.getId());
    }

    private record Fixture(Long userId, Long groupId) {}
}
