package com.teamproject.assistant;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.assistant.application.AiDocumentAutoIndexRetryScheduler;
import com.teamproject.assistant.application.AiDocumentSearchService;
import com.teamproject.assistant.application.port.EmbeddingGateway;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.resource.application.ResourceService;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = B2BGearViaApplication.class,
        properties = {
                "app.storage.local-root=target/test-uploads",
                "app.ai-assistant.enabled=true",
                // AiDocumentAutoIndexRetryScheduler's early-exit guard reads OpenAiReportProperties
                // (shared with the weekly-report gateway), which binds app.ai-report.api-key —
                // not app.openai.api-key. Setting the wrong one leaves the guard tripped and retry()
                // silently no-ops both times, which looks like a business-logic failure, not a config one.
                "app.ai-report.api-key=test-key",
        })
@Transactional
class AiDocumentAutoIndexRetrySchedulerTest {
    @MockBean EmbeddingGateway embeddings;
    @Autowired AiDocumentAutoIndexRetryScheduler scheduler;
    @Autowired AiDocumentSearchService searchService;
    @Autowired ResourceService resources;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired JdbcTemplate jdbc;

    @Test
    void retriesAResourceWhoseAutomaticIndexingFailedTheFirstTime() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        User user = users.save(new User("retry_" + suffix, "retry_" + suffix + "@example.com",
                "hash", "재시도 사용자", true));
        Group group = groups.save(Group.team("자동재시도 그룹", null, "Asia/Seoul", user));
        members.save(GroupMember.leader(group, user));

        Mockito.when(embeddings.modelId()).thenReturn("test-embedding-model");
        Mockito.when(embeddings.embed(Mockito.anyList()))
                .thenThrow(new RuntimeException("일시적 임베딩 API 오류"))
                .thenAnswer(invocation -> {
                    List<String> texts = invocation.getArgument(0);
                    return texts.stream().map(text -> new float[] {1f, 0.5f}).toList();
                });

        resources.upload(user.getId(), group.getId(), null, "배포 절차서",
                new MockMultipartFile("file", "배포.txt", "text/plain",
                        "금요일에는 배포하지 않는다.".getBytes(StandardCharsets.UTF_8)));

        scheduler.retry();
        assertThat(searchService.search(group.getId(), "금요일 배포 규칙", 5)).isEmpty();

        releaseRetryLock();
        scheduler.retry();
        assertThat(searchService.search(group.getId(), "금요일 배포 규칙", 5)).isNotEmpty();
    }

    // 락 리스 기간(15분) 안에 테스트가 두 번 부르므로, 두 번째 소집이 락에 막히지 않도록 직접 푼다.
    private void releaseRetryLock() {
        jdbc.update("UPDATE scheduled_job_locks SET locked_until = ? WHERE name = ?",
                LocalDateTime.now().minusMinutes(1), "ai-document-auto-index-retry");
    }
}
