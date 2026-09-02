package com.teamproject.common.presentation.health;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {
    private final DependencyReadiness readiness = mock(DependencyReadiness.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(readiness)).build();

    @Test
    void livenessDoesNotDependOnExternalResources() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessDoesNotExposeDependencyNamesOrErrors() throws Exception {
        when(readiness.check()).thenReturn(DependencyReadiness.ReadinessSnapshot.down(
                "database", "connection refused at mysql.internal"));

        mvc.perform(get("/api/v1/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(content().string(not(containsString("database"))))
                .andExpect(content().string(not(containsString("connection refused"))))
                .andExpect(content().string(not(containsString("mysql.internal"))));
    }
}
