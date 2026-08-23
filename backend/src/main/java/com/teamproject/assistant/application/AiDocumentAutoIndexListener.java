package com.teamproject.assistant.application;

import com.teamproject.assistant.domain.AiDocumentSource;
import com.teamproject.project.application.ProjectDocumentUploadedEvent;
import com.teamproject.resource.application.ResourceUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 자료 업로드가 끝난 뒤 그 한 건만 색인한다.
 *
 * <p>업로드 응답을 느리게 하지 않으려고 비동기로 돌리고(@Async), 업로드 트랜잭션이 실제로
 * 커밋된 경우에만 실행해(AFTER_COMMIT) 롤백된 업로드를 색인하는 일이 없게 한다.
 */
@Component
public class AiDocumentAutoIndexListener {
    private static final Logger log = LoggerFactory.getLogger(AiDocumentAutoIndexListener.class);

    private final AiDocumentIndexService indexService;

    public AiDocumentAutoIndexListener(AiDocumentIndexService indexService) {
        this.indexService = indexService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResourceUploaded(ResourceUploadedEvent event) {
        try {
            indexService.indexResource(AiDocumentSource.GROUP_RESOURCE, event.groupId(), event.resourceId());
        } catch (RuntimeException exception) {
            log.warn("자료 {} 자동 색인 실패: {}", event.resourceId(), exception.getClass().getSimpleName());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectDocumentUploaded(ProjectDocumentUploadedEvent event) {
        try {
            indexService.indexResource(AiDocumentSource.PROJECT_DOCUMENT, event.groupId(), event.documentId());
        } catch (RuntimeException exception) {
            log.warn("프로젝트 파일 {} 자동 색인 실패: {}", event.documentId(), exception.getClass().getSimpleName());
        }
    }
}
