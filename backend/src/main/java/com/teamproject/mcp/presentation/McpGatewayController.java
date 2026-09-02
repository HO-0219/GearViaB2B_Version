package com.teamproject.mcp.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.mcp.application.McpTokenService;
import com.teamproject.mcp.application.McpToolCatalog;
import com.teamproject.mcp.application.McpRequestLimiter;
import com.teamproject.mcp.security.McpNetworkPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class McpGatewayController {
    private static final String LATEST = "2025-11-25";
    private static final Set<String> SUPPORTED = Set.of(LATEST, "2025-06-18", "2025-03-26");
    private final McpNetworkPolicy networks;
    private final McpTokenService tokens;
    private final McpToolCatalog tools;
    private final McpRequestLimiter limiter;

    public McpGatewayController(McpNetworkPolicy networks, McpTokenService tokens, McpToolCatalog tools,
            McpRequestLimiter limiter) {
        this.networks = networks; this.tokens = tokens; this.tools = tools; this.limiter = limiter;
    }

    @PostMapping(value = "/mcp", consumes = "application/json", produces = "application/json")
    ResponseEntity<?> post(@RequestBody JsonNode message, HttpServletRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "MCP-Protocol-Version", required = false) String protocolVersion,
            @RequestHeader(name = "X-MCP-Client", required = false) String clientLabel) {
        networks.requireAllowed(request.getRemoteAddr(), origin);
        McpTokenService.AuthenticatedToken auth = tokens.authenticate(bearer(authorization),
                request.getRemoteAddr(), clientLabel);
        JsonNode id = message.get("id");
        String method = message.path("method").asText("");
        if (id == null) return ResponseEntity.accepted().build();
        if (!"initialize".equals(method)) requireProtocol(protocolVersion);
        try {
            Object result = switch (method) {
                case "initialize" -> initialize(message.path("params").path("protocolVersion").asText());
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools", tools.tools());
                case "tools/call" -> callTool(auth, message, request);
                default -> null;
            };
            return result == null ? ResponseEntity.ok(error(id, -32601, "Method not found."))
                    : ResponseEntity.ok(success(id, result));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.ok(error(id, -32602, exception.getMessage()));
        }
    }

    private McpToolCatalog.ToolResult callTool(McpTokenService.AuthenticatedToken auth, JsonNode message,
            HttpServletRequest request) {
        try (McpRequestLimiter.Lease ignored = limiter.enter(auth.tokenId())) {
            return tools.call(auth, message.path("params").path("name").asText(),
                    message.path("params").path("arguments"), request.getRemoteAddr(), correlation(request));
        }
    }

    private Map<String, Object> initialize(String requested) {
        String version = SUPPORTED.contains(requested) ? requested : LATEST;
        return Map.of("protocolVersion", version,
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of("name", "GearVia", "version", "1.0"));
    }
    private void requireProtocol(String value) {
        String version = value == null || value.isBlank() ? "2025-03-26" : value;
        if (!SUPPORTED.contains(version)) throw new ApplicationException("MCP_PROTOCOL_UNSUPPORTED",
                HttpStatus.BAD_REQUEST, "MCP protocol version is not supported.");
    }
    private String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) throw new ApplicationException("MCP_TOKEN_REQUIRED",
                HttpStatus.UNAUTHORIZED, "MCP token is required.");
        return header.substring(7);
    }
    private String correlation(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString()
                : value.substring(0, Math.min(80, value.length()));
    }
    private Map<String, Object> success(JsonNode id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0"); response.put("id", id); response.put("result", result); return response;
    }
    private Map<String, Object> error(JsonNode id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0"); response.put("id", id);
        response.put("error", Map.of("code", code, "message", message)); return response;
    }
}
