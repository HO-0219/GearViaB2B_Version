package com.teamproject.mcp;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.mcp.application.McpTokenService;
import com.teamproject.mcp.domain.McpPersonalTokenRepository;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = B2BGearViaApplication.class)
class McpTokenServiceTest {
    @Autowired McpTokenService tokens;
    @Autowired McpPersonalTokenRepository tokenRepository;
    @Autowired SignupService signup;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;

    @Test
    void returnsSecretOnceAndStoresOnlyItsHash() {
        long userId = account("mcp_token_once");

        McpTokenService.CreatedToken created = tokens.create(userId, "My Codex", 30);
        var stored = tokenRepository.findById(created.id()).orElseThrow();

        assertThat(created.token()).startsWith("gv_mcp_").hasSizeGreaterThan(50);
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(created.token());
        assertThat(tokens.list(userId).getFirst().label()).isEqualTo("My Codex");
        assertThat(tokens.list(userId).getFirst().token()).isNull();
    }

    @Test
    void revocationImmediatelyPreventsAuthentication() {
        long userId = account("mcp_token_revoke");
        McpTokenService.CreatedToken created = tokens.create(userId, "Claude", 7);

        assertThat(tokens.authenticate(created.token(), "10.20.30.40", "claude").userId())
                .isEqualTo(userId);
        tokens.revoke(userId, created.id());

        assertThatThrownBy(() -> tokens.authenticate(created.token(), "10.20.30.40", "claude"))
                .hasMessageContaining("MCP token");
    }

    @Test
    void doesNotRewriteUsageMetadataForEveryIdenticalRequest() {
        long userId = account("mcp_token_touch");
        McpTokenService.CreatedToken created = tokens.create(userId, "Codex", 30);

        tokens.authenticate(created.token(), "10.0.0.9", "codex");
        var afterFirst = tokenRepository.findById(created.id()).orElseThrow().getLastUsedAt();
        tokens.authenticate(created.token(), "10.0.0.9", "codex");
        var afterSecond = tokenRepository.findById(created.id()).orElseThrow().getLastUsedAt();
        assertThat(afterSecond).isEqualTo(afterFirst);

        tokens.authenticate(created.token(), "10.0.0.10", "codex");
        var afterMoved = tokenRepository.findById(created.id()).orElseThrow();
        assertThat(afterMoved.getLastIp()).isEqualTo("10.0.0.10");
        assertThat(afterMoved.getLastUsedAt()).isAfterOrEqualTo(afterFirst);
    }

    @Test
    void rejectsUnboundedLabelsAndExpiry() {
        long userId = account("mcp_token_bounds");

        assertThatThrownBy(() -> tokens.create(userId, "x".repeat(61), 30))
                .hasMessageContaining("label");
        assertThatThrownBy(() -> tokens.create(userId, "Codex", 366))
                .hasMessageContaining("expiry");
    }

    private long account(String username) {
        String email = username + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "MCP 사용자", "password123!", code));
        return users.findByUsernameIgnoreCase(username).orElseThrow().getId();
    }
}
