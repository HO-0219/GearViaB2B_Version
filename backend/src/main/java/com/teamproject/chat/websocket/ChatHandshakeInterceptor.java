package com.teamproject.chat.websocket;

import com.teamproject.chat.application.ChatSocketTicketService;
import com.teamproject.deployment.application.PublicUrlProvider;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;
import java.util.Arrays;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    private final ChatSocketTicketService tickets;
    private final PublicUrlProvider publicUrls;

    public ChatHandshakeInterceptor(ChatSocketTicketService tickets, PublicUrlProvider publicUrls) {
        this.tickets = tickets;
        this.publicUrls = publicUrls;
    }

    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        String origin = request.getHeaders().getOrigin();
        if (origin != null && !publicUrls.isAllowedOrigin(origin)) {
            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return false;
        }
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
