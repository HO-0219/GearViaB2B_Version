package com.teamproject.mcp.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.runtime.InstanceIdentity;
import com.teamproject.group.application.GroupService;
import com.teamproject.mcp.domain.McpToolCallAudit;
import com.teamproject.mcp.domain.McpToolCallAuditRepository;
import com.teamproject.task.application.TaskService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpToolCatalog {
    private final GroupService groups;
    private final TaskService tasks;
    private final McpToolCallAuditRepository audits;
    private final InstanceIdentity instance;
    private final ObjectMapper json;

    public McpToolCatalog(GroupService groups, TaskService tasks, McpToolCallAuditRepository audits,
            InstanceIdentity instance, ObjectMapper json) {
        this.groups = groups; this.tasks = tasks; this.audits = audits; this.instance = instance; this.json = json;
    }

    public List<Map<String, Object>> tools() {
        return List.of(
                tool("gearvia_list_groups", "List groups available to the token owner.", Map.of()),
                tool("gearvia_list_tasks", "List a bounded set of tasks in an authorized group.",
                        Map.of("groupId", Map.of("type", "integer", "minimum", 1))),
                tool("gearvia_get_task", "Get one authorized task by identifier.",
                        Map.of("taskId", Map.of("type", "integer", "minimum", 1))));
    }

    public ToolResult call(McpTokenService.AuthenticatedToken authentication, String name, JsonNode arguments,
            String sourceIp, String correlationId) {
        long started = System.nanoTime();
        String target = null;
        try {
            Map<String, Object> structured;
            switch (name) {
                case "gearvia_list_groups" -> structured = Map.of("groups", groups.list(authentication.userId()));
                case "gearvia_list_tasks" -> {
                    long groupId = requiredId(arguments, "groupId"); target = "group:" + groupId;
                    structured = Map.of("tasks", tasks.list(authentication.userId(), groupId));
                }
                case "gearvia_get_task" -> {
                    long taskId = requiredId(arguments, "taskId"); target = "task:" + taskId;
                    structured = Map.of("task", tasks.get(authentication.userId(), taskId));
                }
                default -> throw new IllegalArgumentException("Unknown MCP tool.");
            }
            audit(authentication, name, target, sourceIp, "SUCCESS", started, correlationId);
            return new ToolResult(List.of(Map.of("type", "text", "text", encode(structured))), structured, false);
        } catch (ApplicationException exception) {
            audit(authentication, name, target, sourceIp, "DENIED", started, correlationId);
            Map<String, Object> error = Map.of("code", exception.code(), "message", exception.getMessage());
            return new ToolResult(List.of(Map.of("type", "text", "text", encode(error))), error, true);
        }
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        return Map.of("name", name, "description", description,
                "inputSchema", Map.of("type", "object", "properties", properties, "required", properties.keySet()));
    }
    private long requiredId(JsonNode arguments, String name) {
        JsonNode value = arguments == null ? null : arguments.get(name);
        if (value == null || !value.canConvertToLong() || value.longValue() < 1) {
            throw new IllegalArgumentException(name + " must be a positive integer.");
        }
        return value.longValue();
    }
    private void audit(McpTokenService.AuthenticatedToken auth, String tool, String target, String source,
            String result, long started, String correlation) {
        long latency = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        audits.save(new McpToolCallAudit(auth.tokenId(), auth.userId(), tool, target, source, result,
                latency, instance.value(), correlation));
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("MCP result encoding failed"); }
    }

    public record ToolResult(List<Map<String, String>> content, Map<String, Object> structuredContent,
            boolean isError) {}
}
