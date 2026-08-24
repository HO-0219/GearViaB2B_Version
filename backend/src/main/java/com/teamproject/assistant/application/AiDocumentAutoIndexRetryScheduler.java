package com.teamproject.assistant.application;

import com.teamproject.aiprovider.infrastructure.openai.DynamicOpenAiSettings;
import com.teamproject.common.scheduling.DatabaseJobLock;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 업로드 직후 자동색인({@link AiDocumentAutoIndexListener})이 실패하면 재시도 없이 그대로
 * 끝난다 — 네트워크 순단이나 임베딩 API 일시 장애 같은 경우 사용자가 재색인 버튼을 직접 눌러야만
 * 복구된다. 이 작업은 TEAM 그룹 전체를 주기적으로 훑어 재색인을 다시 돌린다.
 *
 * <p>WorkTaskFlow(원본)에서는 유료 멤버십 그룹만 돌았지만, B2B는 결제 개념이 없고 AI 활성화
 * 여부가 조직 전체에 걸린 관리자 설정 하나뿐이라 TEAM 그룹 전체를 대상으로 한다. 대신 AI 비서가
 * 아예 꺼져 있거나 API 키가 없으면 그룹을 훑기 전에 조기 종료해 불필요한 DB 조회를 막는다.
 *
 * <p>이미 색인된 자료는 {@link AiDocumentIndexService#reindexGroup} 안에서 파일을 읽거나
 * 임베딩을 부르기 전에 건너뛰므로, 실패한 자료가 없는 그룹을 도는 비용은 DB 조회 몇 번뿐이다.
 */
@Component
public class AiDocumentAutoIndexRetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(AiDocumentAutoIndexRetryScheduler.class);

    private final GroupRepository groups;
    private final AiDocumentIndexService indexService;
    private final DatabaseJobLock locks;
    private final DynamicOpenAiSettings openAi;

    public AiDocumentAutoIndexRetryScheduler(GroupRepository groups, AiDocumentIndexService indexService,
            DatabaseJobLock locks, DynamicOpenAiSettings openAi) {
        this.groups = groups;
        this.indexService = indexService;
        this.locks = locks;
        this.openAi = openAi;
    }

    @Scheduled(cron = "${app.ai-assistant.auto-index-retry-cron:0 */20 * * * *}", zone = "Asia/Seoul")
    public void retry() {
        if (!openAi.assistantEnabled() || !openAi.hasApiKey()) return;
        if (!locks.acquire("ai-document-auto-index-retry", Duration.ofMinutes(15))) return;
        for (Group group : groups.findAllByTypeOrderByCreatedAtDesc(Group.Type.TEAM)) {
            try {
                indexService.reindexGroup(group.getId());
            } catch (RuntimeException exception) {
                log.warn("그룹 {} 자동 색인 재시도 실패: {}", group.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
