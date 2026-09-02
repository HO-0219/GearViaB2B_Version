package com.teamproject.mcp.presentation;

import com.teamproject.mcp.application.McpTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me/mcp-tokens")
public class McpTokenController {
    private final McpTokenService tokens;
    public McpTokenController(McpTokenService tokens) { this.tokens = tokens; }

    @GetMapping List<McpTokenService.TokenView> list(Authentication auth) {
        return tokens.list((Long) auth.getPrincipal());
    }
    @PostMapping McpTokenService.CreatedToken create(Authentication auth, @RequestBody CreateRequest request) {
        return tokens.create((Long) auth.getPrincipal(), request.label(), request.expiryDays());
    }
    @DeleteMapping("/{tokenId}") ResponseEntity<Void> revoke(Authentication auth, @PathVariable Long tokenId) {
        tokens.revoke((Long) auth.getPrincipal(), tokenId);
        return ResponseEntity.noContent().build();
    }
    public record CreateRequest(String label, int expiryDays) {}
}
