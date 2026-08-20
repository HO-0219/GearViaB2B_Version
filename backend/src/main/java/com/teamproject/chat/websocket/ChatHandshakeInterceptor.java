package com.teamproject.chat.websocket;

import com.teamproject.chat.application.ChatSocketTicketService;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;
import java.util.Arrays;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    private final ChatSocketTicketService tickets;
    public ChatHandshakeInterceptor(ChatSocketTicketService tickets) { this.tickets = tickets; }
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        String ticket = protocols == null ? null : Arrays.stream(protocols.split(","))
                .map(String::trim).filter(value -> value.startsWith("ticket."))
                .map(value -> value.substring("ticket.".length())).findFirst().orElse(null);
        try { attributes.put("userId", tickets.consume(ticket)); return true; }
        catch (RuntimeException exception) { response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED); return false; }
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {}
}
