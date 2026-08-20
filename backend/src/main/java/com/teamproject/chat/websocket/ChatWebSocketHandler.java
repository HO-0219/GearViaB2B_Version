package com.teamproject.chat.websocket;

import com.fasterxml.jackson.databind.*;
import com.teamproject.chat.application.*;
import com.teamproject.common.exception.ApplicationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.*;
import java.util.concurrent.*;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private final ChatService chat;
    private final ObjectMapper json;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Set<String>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Long>> sessionChannels = new ConcurrentHashMap<>();
    public ChatWebSocketHandler(ChatService chat, ObjectMapper json) { this.chat = chat; this.json = json; }
    @Override public List<String> getSubProtocols() { return List.of("chat"); }

    @Override public void afterConnectionEstablished(WebSocketSession raw) {
        sessions.put(raw.getId(), new ConcurrentWebSocketSessionDecorator(raw, 10_000, 512 * 1024));
        sessionChannels.put(raw.getId(), ConcurrentHashMap.newKeySet());
    }
    @Override protected void handleTextMessage(WebSocketSession raw, TextMessage text) {
        WebSocketSession session = sessions.get(raw.getId()); if (session == null) return;
        try {
            JsonNode value = json.readTree(text.getPayload()); String action = value.path("action").asText();
            long channelId = value.path("channelId").asLong(0); Long userId = (Long) raw.getAttributes().get("userId");
            if (channelId < 1 || userId == null) throw new IllegalArgumentException();
            if ("SUBSCRIBE".equals(action)) {
                chat.requireChannelAccess(userId, channelId);
                subscribers.computeIfAbsent(channelId, ignored -> ConcurrentHashMap.newKeySet()).add(raw.getId());
                sessionChannels.get(raw.getId()).add(channelId);
                send(session, Map.of("type", "SUBSCRIBED", "channelId", channelId));
            } else if ("SEND".equals(action)) {
                if (!sessionChannels.get(raw.getId()).contains(channelId)) throw new ApplicationException(
                        "CHAT_SUBSCRIPTION_REQUIRED", org.springframework.http.HttpStatus.CONFLICT,
                        "채팅방 구독이 완료된 후 메시지를 보내 주세요.");
                chat.sendText(userId, channelId, value.path("content").asText());
            } else throw new IllegalArgumentException();
        } catch (ApplicationException exception) { sendError(session, exception.code(), exception.getMessage()); }
        catch (Exception exception) { sendError(session, "CHAT_SOCKET_MESSAGE_INVALID", "올바르지 않은 채팅 요청입니다."); }
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void message(ChatMessageEvent event) {
        Set<String> ids = subscribers.getOrDefault(event.channelId(), Set.of());
        for (String id : ids) { WebSocketSession session = sessions.get(id);
            if (session != null && session.isOpen()) send(session, Map.of("type", "MESSAGE", "message", event.message())); }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) { remove(session.getId()); }
    private void remove(String id) { sessions.remove(id); Set<Long> channels = sessionChannels.remove(id);
        if (channels != null) channels.forEach(channel -> { Set<String> ids = subscribers.get(channel);
            if (ids != null) { ids.remove(id); if (ids.isEmpty()) subscribers.remove(channel, ids); }}); }
    private void sendError(WebSocketSession session, String code, String message) { send(session,
            Map.of("type", "ERROR", "code", code, "message", message)); }
    private void send(WebSocketSession session, Object value) { try { if (session.isOpen())
        session.sendMessage(new TextMessage(json.writeValueAsString(value))); } catch (Exception ignored) {} }
}
