package com.teamproject.admin;

import com.teamproject.admin.application.dto.AdminDtos.AdminUserResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class B2bUserAdministrationApiTest {
    @Test
    void adminUserResponseNeverExposesPasswordOrAuthenticationSecrets() {
        assertThat(Arrays.stream(AdminUserResponse.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("password", "passwordHash", "mfaSecret", "recoveryCodes");
    }
}
