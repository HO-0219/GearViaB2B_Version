package com.teamproject.aiprovider.application;

import com.teamproject.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderPolicyTest {
    private final AiProviderPolicy policy = new AiProviderPolicy();

    @Test
    void acceptsOpenAiOnlyWhenExternalAccessIsExplicitlyAllowed() {
        var profile = policy.validate("OPENAI", "https://api.openai.com/v1", "gpt-5.6-sol", "text-embedding-3-small", 45, true);

        assertThat(profile.provider()).isEqualTo(AiProviderProfile.Provider.OPENAI);
        assertThat(profile.baseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void rejectsOpenAiWhenInternetAccessIsDisabled() {
        assertThatThrownBy(() -> policy.validate(
                "OPENAI", "https://api.openai.com/v1", "gpt-5.6-sol", "embed", 45, false))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("외부");
    }

    @Test
    void acceptsPrivateOpenAiCompatibleEndpointsWithoutExternalAccess() {
        assertThat(policy.validate("INTERNAL_OPENAI_COMPATIBLE", "http://10.20.0.15:8000/v1",
                "company-chat", "company-embed", 30, false).baseUrl()).isEqualTo("http://10.20.0.15:8000/v1");
        assertThat(policy.validate("INTERNAL_OPENAI_COMPATIBLE", "https://192.168.10.20/v1",
                "company-chat", "company-embed", 30, false).baseUrl()).isEqualTo("https://192.168.10.20/v1");
    }

    @Test
    void acceptsAnInternalHostnameThatResolvesToAPrivateAddress() {
        // "localhost" is a name, not an IP literal — it must survive DNS resolution.
        assertThat(policy.validate("INTERNAL_OPENAI_COMPATIBLE", "http://localhost:11434/v1",
                "company-chat", "company-embed", 30, false).baseUrl())
                .isEqualTo("http://localhost:11434/v1");
    }

    @Test
    void rejectsPublicOrCredentialBearingInternalEndpoints() {
        assertThatThrownBy(() -> policy.validate("INTERNAL_OPENAI_COMPATIBLE",
                "https://example.com/v1", "model", "embed", 30, false)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> policy.validate("INTERNAL_OPENAI_COMPATIBLE",
                "http://user:pass@10.0.0.2/v1", "model", "embed", 30, false)).isInstanceOf(ApplicationException.class);
    }

    @Test
    void validatesModelAndTimeoutBounds() {
        assertThatThrownBy(() -> policy.validate("INTERNAL_OPENAI_COMPATIBLE",
                "http://127.0.0.1:8000/v1", "", "embed", 30, false)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> policy.validate("INTERNAL_OPENAI_COMPATIBLE",
                "http://127.0.0.1:8000/v1", "model", "embed", 121, false)).isInstanceOf(ApplicationException.class);
    }
}
