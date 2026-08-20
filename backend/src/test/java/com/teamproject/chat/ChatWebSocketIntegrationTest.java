package com.teamproject.chat;

import com.fasterxml.jackson.databind.*;
import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.*;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.chat.application.ChatSocketTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChatWebSocketIntegrationTest {
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired ChatSocketTicketService socketTickets;

    @Test
    void subscribesAndReceivesPersistedMessageOverRealWebSocket() throws Exception {
        String token = signupAndLogin("socket_live_user", "socket-live@example.com");
        long groupId = number(mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"실시간 소켓팀\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        long channelId = number(mvc.perform(get("/api/v1/groups/{groupId}/chat/channels", groupId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), "$[0].id");
        String ticket = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/v1/chat/socket-tickets")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.ticket");

        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<JsonNode> liveMessage = new AtomicReference<>();
        var client = new StandardWebSocketClient();
        var headers = new WebSocketHttpHeaders(); headers.setOrigin("http://localhost:5174");
        headers.setSecWebSocketProtocol(java.util.List.of("chat", "ticket." + ticket));
        WebSocketSession socket = client.execute(new TextWebSocketHandler() {
            @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                JsonNode payload = json.readTree(message.getPayload());
                if (payload.path("type").asText().equals("SUBSCRIBED")) subscribed.countDown();
                if (payload.path("type").asText().equals("MESSAGE")) {
                    liveMessage.set(payload.path("message")); received.countDown();
                }
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/chat")).get(5, TimeUnit.SECONDS);
        try {
            socket.sendMessage(new TextMessage("{\"action\":\"SUBSCRIBE\",\"channelId\":" + channelId + "}"));
            assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
            socket.sendMessage(new TextMessage("{\"action\":\"SEND\",\"channelId\":" + channelId
                    + ",\"content\":\"WebSocket 실시간 메시지\"}"));
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(liveMessage.get().path("content").asText()).isEqualTo("WebSocket 실시간 메시지");
            assertThat(liveMessage.get().path("channelId").asLong()).isEqualTo(channelId);
        } finally { socket.close(); }
        mvc.perform(get("/api/v1/chat/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
    @Test
    void socketTicketCanOnlyBeConsumedOnceUnderConcurrency() throws Exception {
        String token = signupAndLogin("socket_race_user", "socket-race@example.com");
        String ticket = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/v1/chat/socket-tickets")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.ticket");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        try {
            Future<?> first = pool.submit(() -> consumeAfter(start, ticket, successes));
            Future<?> second = pool.submit(() -> consumeAfter(start, ticket, successes));
            start.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(successes.get()).isEqualTo(1);
    }
    private void consumeAfter(CountDownLatch start, String ticket, AtomicInteger successes) {
        try { start.await(); socketTickets.consume(ticket); successes.incrementAndGet(); }
        catch (Exception ignored) { }
    }
    private long number(String source, String path) { return ((Number)
            com.jayway.jsonpath.JsonPath.read(source, path)).longValue(); }
    private String signupAndLogin(String username, String email) {
        String code = tokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "소켓 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
