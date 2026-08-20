package com.teamproject.chat.application;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {
    private static final int LIMIT = 20;
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private final ConcurrentHashMap<Long, ArrayDeque<Instant>> sends = new ConcurrentHashMap<>();
    public void check(Long userId) {
        Instant now = Instant.now();
        ArrayDeque<Instant> values = sends.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (values) {
            while (!values.isEmpty() && values.peekFirst().isBefore(now.minus(WINDOW))) values.removeFirst();
            if (values.size() >= LIMIT) throw new ApplicationException(
                    "CHAT_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "메시지를 너무 빠르게 보내고 있습니다.");
            values.addLast(now);
        }
        if (sends.size() > 10_000) sends.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) { return entry.getValue().isEmpty()
                    || entry.getValue().peekLast().isBefore(now.minus(Duration.ofMinutes(5))); }
        });
    }
}
