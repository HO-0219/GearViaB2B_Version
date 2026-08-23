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
import com.teamproject.project.application.ProjectDocumentService;
import com.teamproject.project.domain.Project;
import com.teamproject.project.domain.ProjectRepository;
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

/**
 * 프로젝트 파일함({@link com.teamproject.project.domain.ProjectDocument})도 그룹 자료실과
 * 같은 방식으로 RAG 색인·검색 대상이 되는지 확인한다. 색인 메커니즘 자체는
 * {@link AiDocumentRagTest}(그룹 자료실)에서 이미 자세히 검증했으므로, 여기서는 두 출처가
 * 섞여도 서로 침범하지 않는지에 집중한다.
 */
@SpringBootTest(classes = B2BGearViaApplication.class,
        properties = "app.storage.local-root=target/test-uploads")
@Transactional
class AiDocumentProjectDocumentRagTest {
    private static final List<String> VOCABULARY = List.of("금요일", "배포", "휴가");

    @MockBean EmbeddingGateway embeddings;
    @Autowired AiDocumentIndexService indexService;
    @Autowired AiDocumentSearchService searchService;
    @Autowired ProjectDocumentService documents;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @BeforeEach
    void stubEmbeddings() {
        Mockito.when(embeddings.modelId()).thenReturn("test-embedding-model");
        Mockito.when(embeddings.embed(Mockito.anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(AiDocumentProjectDocumentRagTest::vector).toList();
        });
    }

    @Test
    void indexesAProjectFileAndFindsItInSearch() {
        Fixture fixture = fixture();
        upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다. 배포는 화요일과 목요일에만 한다.");

        var result = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isNotEmpty();
    }

    @Test
    void groupResourceAndProjectDocumentIdsDoNotCollide() {
        Fixture fixture = fixture();
        // 두 출처 모두 자동증가 PK를 쓰므로 같은 그룹 안에서 우연히 같은 ID를 받을 수 있다.
        upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        upload(fixture, "휴가 규정", "휴가.txt", "휴가는 사흘 전까지 신청한다.");

        var result = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(result.indexed()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        var passages = searchService.search(fixture.groupId(), "금요일 배포 규칙", 5);
        assertThat(passages).isNotEmpty();
        assertThat(passages.get(0).title()).isEqualTo("배포 절차서");
    }

    @Test
    void excludesADeletedProjectFileFromSearchBeforeTheNextReindex() {
        Fixture fixture = fixture();
        Long documentId = upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        indexService.reindex(fixture.userId(), fixture.groupId());
        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isNotEmpty();

        documents.delete(fixture.userId(), documentId);

        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isEmpty();
    }

    @Test
    void removesChunksForAProjectFileWhenItIsDeleted() {
        Fixture fixture = fixture();
        Long documentId = upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");
        indexService.reindex(fixture.userId(), fixture.groupId());
        documents.delete(fixture.userId(), documentId);

        var result = indexService.reindex(fixture.userId(), fixture.groupId());

        assertThat(result.removed()).isEqualTo(1);
    }

    @Test
    void indexesASingleProjectFileWithoutWaitingForReindex() {
        Fixture fixture = fixture();
        Long documentId = upload(fixture, "배포 절차서", "배포.txt", "금요일에는 배포하지 않는다.");

        indexService.indexResource(AiDocumentSource.PROJECT_DOCUMENT, fixture.groupId(), documentId);

        assertThat(searchService.search(fixture.groupId(), "금요일 배포 규칙", 5)).isNotEmpty();
    }

    private static float[] vector(String text) {
        float[] values = new float[VOCABULARY.size()];
        for (int index = 0; index < VOCABULARY.size(); index++) {
            values[index] = text.contains(VOCABULARY.get(index)) ? 1f : 0f;
        }
        for (int index = 0; index < values.length; index++) values[index] += 0.01f;
        return values;
    }

    private Long upload(Fixture fixture, String title, String filename, String content) {
        return documents.upload(fixture.userId(), fixture.projectId(), null, title,
                new MockMultipartFile("file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8))).id();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        User user = users.save(new User("rag_pd_" + suffix, "rag_pd_" + suffix + "@example.com",
                "hash", "RAG 사용자", true));
        Group group = groups.save(Group.team("RAG 프로젝트 테스트 그룹", null, "Asia/Seoul", user));
        GroupMember leader = members.save(GroupMember.leader(group, user));
        Project project = projects.save(new Project(group, leader, leader, "RAG 테스트 프로젝트", null, null, null));
        return new Fixture(user.getId(), group.getId(), project.getId());
    }

    private record Fixture(Long userId, Long groupId, Long projectId) {}
}
