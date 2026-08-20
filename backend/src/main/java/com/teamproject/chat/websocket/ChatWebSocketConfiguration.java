package com.teamproject.chat.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfiguration implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor handshake;
    private final String frontendUrl;
    public ChatWebSocketConfiguration(ChatWebSocketHandler handler, ChatHandshakeInterceptor handshake,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.handler = handler; this.handshake = handshake; this.frontendUrl = frontendUrl;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat").addInterceptors(handshake).setAllowedOrigins(frontendUrl);
    }
}
