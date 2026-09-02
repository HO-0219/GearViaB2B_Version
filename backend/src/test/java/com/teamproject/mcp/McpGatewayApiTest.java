package com.teamproject.mcp;

import com.jayway.jsonpath.JsonPath;
import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.group.application.GroupService;
import com.teamproject.group.application.dto.GroupDtos.CreateGroupRequest;
import com.teamproject.mcp.application.McpTokenService;
import com.teamproject.mcp.domain.McpToolCallAuditRepository;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = B2BGearViaApplication.class, properties = {
        "app.mcp.enabled=true",
        "app.mcp.allowed-cidrs=127.0.0.1/32,10.0.0.0/8",
        "app.mcp.trusted-proxies=172.16.0.0/12",
        "app.mcp.allowed-origins=https://gearvia.internal",
        "app.mcp.max-tool-calls-per-minute=1",
        "app.mcp.max-concurrent-calls=2"
})
@AutoConfigureMockMvc
class McpGatewayApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired McpTokenService tokens;
    @Autowired GroupService groups;
    @Autowired McpToolCallAuditRepository audits;

    @Test
    void initializesListsAndCallsBoundedReadTools() throws Exception {
        Account account = account("mcp_gateway");
        groups.createTeam(account.userId(), new CreateGroupRequest("MCP 업무팀", null, "Asia/Seoul"));

        mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                        .header("Authorization", "Bearer " + account.token())
                        .header("Accept", "application/json, text/event-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"codex","version":"1"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"))
                .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false));

        mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                        .header("Authorization", "Bearer " + account.token())
                        .header("MCP-Protocol-Version", "2025-11-25")
                        .header("Accept", "application/json, text/event-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools.length()").value(3))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("object"));

        var call = mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                        .header("Authorization", "Bearer " + account.token())
                        .header("MCP-Protocol-Version", "2025-11-25")
                        .header("Accept", "application/json, text/event-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"gearvia_list_groups","arguments":{}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.groups.length()").value(2))
                .andReturn();
        assertThat(call.getResponse().getContentAsString()).doesNotContain(account.token());
        assertThat(audits.count()).isEqualTo(1);

        mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                        .header("Authorization", "Bearer " + account.token())
                        .header("MCP-Protocol-Version", "2025-11-25")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"gearvia_list_groups","arguments":{}}}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MCP_RATE_LIMITED"));
    }

    @Test
    void rejectsOutsideNetworksAndInvalidOriginsBeforeToolExecution() throws Exception {
        Account account = account("mcp_network");
        String body = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/list\",\"params\":{}}";

        mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("203.0.113.9"); return request; })
                        .header("Authorization", "Bearer " + account.token())
                        .header("MCP-Protocol-Version", "2025-11-25")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                        .header("Authorization", "Bearer " + account.token())
                        .header("Origin", "https://evil.example")
                        .header("MCP-Protocol-Version", "2025-11-25")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(audits.count()).isZero();
    }

    @Test
    void trustsForwardedSourceOnlyFromTheConfiguredReverseProxyNetwork() throws Exception {
        Account account = account("mcp_proxy_source");
        String body = "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/list\",\"params\":{}}";

        mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("172.20.0.2"); return request; })
                        .header("X-Forwarded-For", "203.0.113.8")
                        .header("Authorization", "Bearer " + account.token())
                        .header("MCP-Protocol-Version", "2025-11-25")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/mcp").with(request -> { request.setRemoteAddr("172.20.0.2"); return request; })
                        .header("X-Forwarded-For", "10.20.30.40")
                        .header("Authorization", "Bearer " + account.token())
                        .header("MCP-Protocol-Version", "2025-11-25")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private Account account(String username) {
        String email = username + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "MCP Gateway 사용자", "password123!", code));
        long userId = users.findByUsernameIgnoreCase(username).orElseThrow().getId();
        return new Account(userId, tokens.create(userId, "Codex", 30).token());
    }

    private record Account(long userId, String token) {}
}
