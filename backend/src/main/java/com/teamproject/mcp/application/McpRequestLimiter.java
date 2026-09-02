package com.teamproject.mcp.application;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpRequestLimiter {
    private final int maxPerMinute;
    private final int maxConcurrent;
    private final ConcurrentHashMap<Long, State> states = new ConcurrentHashMap<>();

    public McpRequestLimiter(@Value("${app.mcp.max-tool-calls-per-minute:120}") int maxPerMinute,
            @Value("${app.mcp.max-concurrent-calls:2}") int maxConcurrent) {
        if (maxPerMinute < 1 || maxPerMinute > 10_000 || maxConcurrent < 1 || maxConcurrent > 32) {
            throw new IllegalArgumentException("Invalid MCP request limits");
        }
        this.maxPerMinute = maxPerMinute;
        this.maxConcurrent = maxConcurrent;
    }

    public Lease enter(Long tokenId) {
        State state = states.computeIfAbsent(tokenId, ignored -> new State());
        long minute = System.currentTimeMillis() / 60_000;
        synchronized (state) {
            if (state.minute != minute) { state.minute = minute; state.requests = 0; }
            if (state.requests >= maxPerMinute) throw limited("MCP_RATE_LIMITED", "MCP tool rate limit exceeded.");
            if (state.active >= maxConcurrent) throw limited("MCP_CONCURRENCY_LIMITED", "MCP concurrency limit exceeded.");
            state.requests++;
            state.active++;
        }
        return () -> {
            synchronized (state) { state.active = Math.max(0, state.active - 1); }
        };
    }

    private ApplicationException limited(String code, String message) {
        return new ApplicationException(code, HttpStatus.TOO_MANY_REQUESTS, message);
    }

    private static final class State { long minute = -1; int requests; int active; }
    public interface Lease extends AutoCloseable { @Override void close(); }
}
