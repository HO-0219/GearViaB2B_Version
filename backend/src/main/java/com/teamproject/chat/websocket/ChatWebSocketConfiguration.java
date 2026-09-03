package com.teamproject.chat.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfiguration implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor handshake;

    public ChatWebSocketConfiguration(ChatWebSocketHandler handler, ChatHandshakeInterceptor handshake) {
        this.handler = handler;
        this.handshake = handshake;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // The handshake interceptor validates the Origin against the current
        // public URL, so origin enforcement is not pinned at registration time.
        registry.addHandler(handler, "/ws/chat").addInterceptors(handshake).setAllowedOriginPatterns("*");
    }
}
