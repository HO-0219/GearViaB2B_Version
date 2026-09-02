package com.teamproject.admin;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.AccessSessionIssuer;
import com.teamproject.authentication.domain.token.RefreshToken.ClientMode;
import com.teamproject.authentication.domain.token.SessionDevice;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = B2BGearViaApplication.class, properties = {
        "app.admin.enabled=true",
        "app.admin.allowed-ips=127.0.0.1",
        "app.storage.provider=local",
        "app.storage.local-root=target/monitoring-api-uploads"
})
@AutoConfigureMockMvc
@Transactional
class AdminMonitoringApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AccessSessionIssuer sessions;

    @Test
    void mfaVerifiedAdministratorReceivesSystemAndAiUsageOverview() throws Exception {
        User administrator = new User("monitoring_admin", "monitoring-admin@example.com", "hash", "Monitoring Admin", true);
        administrator.promoteToAdmin();
        users.save(administrator);
        String token = sessions.issue(administrator, ClientMode.WEB, SessionDevice.unknown(), true)
                .response().accessToken();

        mvc.perform(get("/api/v1/admin/monitoring")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system.cpu.available").isBoolean())
                .andExpect(jsonPath("$.system.memory.available").isBoolean())
                .andExpect(jsonPath("$.system.storage.provider").value("local"))
                .andExpect(jsonPath("$.runtime.instanceId").isNotEmpty())
                .andExpect(jsonPath("$.runtime.maxTaskResults").value(1000))
                .andExpect(jsonPath("$.databasePool.available").isBoolean())
                .andExpect(jsonPath("$.dependencies").isArray())
                .andExpect(jsonPath("$.executors").isArray())
                .andExpect(jsonPath("$.alerts").isArray())
                .andExpect(jsonPath("$.aiUsage.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.aiUsage.periods.today").exists())
                .andExpect(jsonPath("$.aiUsage.periods.thisMonth").exists())
                .andExpect(jsonPath("$.aiUsage.periods.allTime").exists())
                .andExpect(jsonPath("$.aiUsage.breakdown").isArray());
    }
}
