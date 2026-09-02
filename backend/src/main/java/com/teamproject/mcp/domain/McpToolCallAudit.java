package com.teamproject.mcp.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mcp_tool_call_audits")
public class McpToolCallAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "token_id", nullable = false) private Long tokenId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "tool_name", nullable = false, length = 80) private String toolName;
    @Column(name = "target_id", length = 100) private String targetId;
    @Column(name = "source_ip", nullable = false, length = 64) private String sourceIp;
    @Column(nullable = false, length = 20) private String result;
    @Column(name = "latency_ms", nullable = false) private long latencyMs;
    @Column(name = "instance_id", nullable = false, length = 80) private String instanceId;
    @Column(name = "correlation_id", nullable = false, length = 80) private String correlationId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected McpToolCallAudit() {}
    public McpToolCallAudit(Long tokenId, Long userId, String toolName, String targetId,
            String sourceIp, String result, long latencyMs, String instanceId, String correlationId) {
        this.tokenId = tokenId; this.userId = userId; this.toolName = toolName; this.targetId = targetId;
        this.sourceIp = sourceIp; this.result = result; this.latencyMs = latencyMs;
        this.instanceId = instanceId; this.correlationId = correlationId; this.createdAt = LocalDateTime.now();
    }
}
