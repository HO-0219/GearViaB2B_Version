package com.teamproject.mcp.application;

import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.mcp.domain.McpPersonalToken;
import com.teamproject.mcp.domain.McpPersonalTokenRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class McpTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final McpPersonalTokenRepository tokens;
    private final UserRepository users;
    private final HashService hashes;

    public McpTokenService(McpPersonalTokenRepository tokens, UserRepository users, HashService hashes) {
        this.tokens = tokens;
        this.users = users;
        this.hashes = hashes;
    }

    @Transactional
    public CreatedToken create(Long userId, String label, int expiryDays) {
        String normalizedLabel = label == null ? "" : label.trim();
        if (normalizedLabel.isEmpty() || normalizedLabel.length() > 60) {
            throw invalid("MCP_TOKEN_LABEL_INVALID", "MCP token label must contain 1 to 60 characters.");
        }
        if (expiryDays < 1 || expiryDays > 365) {
            throw invalid("MCP_TOKEN_EXPIRY_INVALID", "MCP token expiry must be between 1 and 365 days.");
        }
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> invalid("MCP_USER_INACTIVE", "MCP token user is not active."));
        byte[] random = new byte[36];
        RANDOM.nextBytes(random);
        String secret = "gv_mcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(expiryDays);
        McpPersonalToken saved = tokens.save(new McpPersonalToken(user, normalizedLabel, hashes.sha256(secret), expiresAt));
        return new CreatedToken(saved.getId(), saved.getLabel(), secret, saved.getScope(), saved.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<TokenView> list(Long userId) {
        return tokens.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    @Transactional
    public void revoke(Long userId, Long tokenId) {
        McpPersonalToken token = tokens.findById(tokenId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApplicationException("MCP_TOKEN_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "MCP token was not found."));
        token.revoke();
    }

    @Transactional
    public AuthenticatedToken authenticate(String secret, String sourceIp, String clientLabel) {
        String raw = secret == null ? "" : secret.trim();
        McpPersonalToken token = tokens.findByTokenHash(hashes.sha256(raw))
                .filter(value -> value.usableAt(LocalDateTime.now()))
                .orElseThrow(() -> new ApplicationException("MCP_TOKEN_INVALID", HttpStatus.UNAUTHORIZED,
                        "MCP token is invalid or expired."));
        token.used(sourceIp, clientLabel);
        return new AuthenticatedToken(token.getId(), token.getUser().getId(), token.getScope());
    }

    private TokenView view(McpPersonalToken token) {
        return new TokenView(token.getId(), token.getLabel(), null, token.getScope(), token.getCreatedAt(),
                token.getExpiresAt(), token.getLastUsedAt(), token.getLastIp(), token.getClientLabel(),
                token.getRevokedAt());
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(code, HttpStatus.BAD_REQUEST, message);
    }

    public record CreatedToken(Long id, String label, String token, String scope, LocalDateTime expiresAt) {}
    public record TokenView(Long id, String label, String token, String scope, LocalDateTime createdAt,
            LocalDateTime expiresAt, LocalDateTime lastUsedAt, String lastIp, String clientLabel,
            LocalDateTime revokedAt) {}
    public record AuthenticatedToken(Long tokenId, Long userId, String scope) {}
}
