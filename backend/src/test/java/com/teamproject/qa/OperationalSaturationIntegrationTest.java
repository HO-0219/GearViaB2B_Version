package com.teamproject.qa;

import com.jayway.jsonpath.JsonPath;
import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.common.execution.ExecutorTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = B2BGearViaApplication.class, properties = {
        "app.runtime.executors.document-index.core-size=1",
        "app.runtime.executors.document-index.max-size=1",
        "app.runtime.executors.document-index.queue-capacity=1"
})
@AutoConfigureMockMvc
class OperationalSaturationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired ExecutorTelemetry telemetry;
    @Autowired @Qualifier("documentIndexExecutor") ThreadPoolTaskExecutor documentExecutor;
    @Autowired @Qualifier("notificationExecutor") ThreadPoolTaskExecutor notificationExecutor;

    @Test
    void documentSaturationDoesNotBlockInteractiveAndNotificationWorkloads() throws Exception {
        String username = "qa_saturation";
        String email = username + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "포화 검증 사용자", "password123!", code));
        String initialToken = sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
        var group = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(initialToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"포화 검증 팀\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = ((Number) JsonPath.read(group.getResponse().getContentAsString(), "$.id")).longValue();
        var created = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(initialToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"포화 중 조회할 업무\"}"))
                .andExpect(status().isCreated()).andReturn();
        long taskId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        CountDownLatch documentStarted = new CountDownLatch(1);
        CountDownLatch releaseDocument = new CountDownLatch(1);
        CountDownLatch queuedDocumentFinished = new CountDownLatch(1);
        CountDownLatch notificationFinished = new CountDownLatch(1);
        long rejectedBefore = telemetry.snapshot("document-index").rejected();
        try {
            documentExecutor.execute(() -> awaitRelease(documentStarted, releaseDocument));
            assertThat(documentStarted.await(3, TimeUnit.SECONDS)).isTrue();
            documentExecutor.execute(queuedDocumentFinished::countDown);

            assertThatThrownBy(() -> documentExecutor.execute(() -> {}))
                    .isInstanceOf(TaskRejectedException.class);
            assertThat(telemetry.snapshot("document-index").rejected()).isEqualTo(rejectedBefore + 1);

            var login = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"qa_saturation\",\"password\":\"password123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();
            String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

            mvc.perform(get("/api/v1/tasks/{taskId}", taskId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(taskId));
            notificationExecutor.execute(notificationFinished::countDown);
            assertThat(notificationFinished.await(3, TimeUnit.SECONDS)).isTrue();
            mvc.perform(get("/api/v1/health/ready"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        } finally {
            releaseDocument.countDown();
            assertThat(queuedDocumentFinished.await(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void awaitRelease(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
