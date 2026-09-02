package com.teamproject.deployment;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.deployment.domain.DeploymentSettings;
import com.teamproject.deployment.domain.DeploymentSettingsRepository;
import com.teamproject.group.application.GroupInvitationService;
import com.teamproject.group.application.GroupService;
import com.teamproject.group.application.dto.GroupDtos.CreateGroupRequest;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = B2BGearViaApplication.class)
@AutoConfigureMockMvc
@Transactional
class DynamicPublicUrlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired DeploymentSettingsRepository settings;
    @Autowired GroupInvitationService invitationService;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupService groupService;

    @Test
    void mutationFilterBlocksStaleOriginAfterPublicUrlChange() throws Exception {
        settings.save(new DeploymentSettings("https://new.gearvia.corp"));

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, "https://old.gearvia.corp"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, "https://new.gearvia.corp"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    void corsAllowsCurrentPublicUrlAfterChange() throws Exception {
        settings.save(new DeploymentSettings("https://new.gearvia.corp"));

        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://new.gearvia.corp")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://new.gearvia.corp"));
    }

    @Test
    void inviteLinkUsesCurrentPublicUrl() {
        settings.save(new DeploymentSettings("https://new.gearvia.corp"));
        User owner = account("dyn_link_owner", "dyn-link-owner@example.com");
        long teamId = groupService.createTeam(owner.getId(),
                new CreateGroupRequest("동적 URL 팀", null, "Asia/Seoul")).id();

        String url = invitationService.createLink(owner.getId(), teamId).url();

        assertThat(url).startsWith("https://new.gearvia.corp/group-invitations/accept?token=");
    }

    private User account(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "동적 사용자", "password123!", code));
        return users.findByUsernameIgnoreCase(username).orElseThrow();
    }
}
