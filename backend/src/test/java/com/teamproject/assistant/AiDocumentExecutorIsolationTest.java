package com.teamproject.assistant;

import com.teamproject.assistant.application.AiDocumentAutoIndexListener;
import com.teamproject.assistant.application.AiDocumentIndexService;
import com.teamproject.resource.application.ResourceUploadedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class AiDocumentExecutorIsolationTest {
    @Autowired AiDocumentAutoIndexListener listener;
    @MockBean AiDocumentIndexService indexService;

    @Test
    void resourceIndexingRunsOnTheDocumentWorkloadExecutor() throws Exception {
        CountDownLatch indexed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            indexed.countDown();
            return null;
        }).when(indexService).indexResource(any(), eq(10L), eq(20L));

        listener.onResourceUploaded(new ResourceUploadedEvent(10L, 20L));

        assertThat(indexed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith("gearvia-document-index-");
    }
}
