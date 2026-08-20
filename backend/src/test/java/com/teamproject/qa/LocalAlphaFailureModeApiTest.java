package com.teamproject.qa;

import com.jayway.jsonpath.JsonPath;
import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
class LocalAlphaFailureModeApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;

    @Test
    void malformedJsonMissingParameterAndInvalidDateUseStableSafeErrors() throws Exception {
        String token = account("invalid_input");

        mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 형식과 필수 입력값을 확인해 주세요."));
        mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        mvc.perform(get("/api/v1/calendars/events")
                        .param("from", "not-a-date")
                        .param("to", "2030-02-01")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/calendars/events")
                        .param("from", "2030-01-01")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void staleWriteAndDstOverlapAreRejectedWithoutChangingSavedEvent() throws Exception {
        String token = account("failure_boundary");
        var groupResult = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"장애 경계 팀\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = number(groupResult, "$.id");

        var eventResult = mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("정상 일정", "2030-01-10T09:00:00", "2030-01-10T10:00:00")))
                .andExpect(status().isCreated()).andReturn();
        long eventId = number(eventResult, "$.eventId");

        mvc.perform(patch("/api/v1/calendar-events/{eventId}", eventId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"먼저 저장된 일정\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/v1/calendar-events/{eventId}", eventId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"오래된 덮어쓰기\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALENDAR_VERSION_CONFLICT"));
        mvc.perform(get("/api/v1/calendars/events")
                        .param("groupId", String.valueOf(groupId))
                        .param("from", "2030-01-01").param("to", "2030-02-01")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("먼저 저장된 일정"))
                .andExpect(jsonPath("$.items[0].version").value(1));

        mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("DST 중복", "2026-11-01T01:30:00", "2026-11-01T03:00:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALENDAR_LOCAL_TIME_INVALID"));
    }

    private String account(String suffix) {
        String username = "qa_failure_" + suffix;
        String email = username + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "장애 QA 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }

    private String eventJson(String title, String startAt, String endAt) {
        return "{\"type\":\"SCHEDULE\",\"title\":\"" + title + "\",\"startAt\":\""
                + startAt + "\",\"endAt\":\"" + endAt + "\",\"allDay\":false}";
    }

    private long number(org.springframework.test.web.servlet.MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
