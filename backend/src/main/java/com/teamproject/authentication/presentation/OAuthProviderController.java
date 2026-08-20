package com.teamproject.authentication.presentation;

import com.teamproject.authentication.application.dto.OAuthDtos.ProviderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class OAuthProviderController {
    private final boolean google;
    private final boolean kakao;
    public OAuthProviderController(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.kakao.client-id}") String kakaoClientId) {
        this.google = isConfigured(googleClientId, "googlecloudeconsole_");
        this.kakao = isConfigured(kakaoClientId, null);
    }
    @GetMapping("/providers")
    ProviderResponse providers() { return new ProviderResponse(google, kakao); }

    private boolean isConfigured(String value, String placeholderPrefix) {
        return value != null && !value.isBlank() && !"disabled".equals(value)
                && (placeholderPrefix == null || !value.startsWith(placeholderPrefix));
    }
}
