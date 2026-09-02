package com.teamproject.aiprovider.application;

import com.teamproject.common.exception.ApplicationException;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiProviderPolicy {
    public AiProviderProfile validate(String providerValue, String baseUrlValue, String modelValue, String embeddingModelValue,
            int timeoutSeconds, boolean externalAllowed) {
        AiProviderProfile.Provider provider;
        try {
            provider = AiProviderProfile.Provider.valueOf(providerValue == null ? "" : providerValue.trim());
        } catch (IllegalArgumentException exception) {
            throw invalid("지원하지 않는 AI 공급자입니다.");
        }
        String baseUrl = baseUrlValue == null ? "" : baseUrlValue.trim().replaceAll("/+$", "");
        String model = modelValue == null ? "" : modelValue.trim();
        String embeddingModel = embeddingModelValue == null ? "" : embeddingModelValue.trim();
        if (model.isEmpty() || model.length() > 120) throw invalid("AI 모델 이름을 확인해 주세요.");
        if (embeddingModel.isEmpty() || embeddingModel.length() > 120) throw invalid("임베딩 모델 이름을 확인해 주세요.");
        if (timeoutSeconds < 1 || timeoutSeconds > 120) throw invalid("AI 요청 제한 시간은 1~120초여야 합니다.");

        URI uri;
        try { uri = URI.create(baseUrl); }
        catch (RuntimeException exception) { throw invalid("AI 서버 URL 형식이 올바르지 않습니다."); }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw invalid("AI 서버 URL은 인증정보·쿼리 없는 HTTP(S) 주소여야 합니다.");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (provider == AiProviderProfile.Provider.OPENAI) {
            if (!externalAllowed) throw invalid("외부 인터넷 접근이 차단되어 OpenAI 공급자를 사용할 수 없습니다.");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"api.openai.com".equals(host)) {
                throw invalid("OpenAI 공급자는 https://api.openai.com 주소만 허용합니다.");
            }
        } else if (!isPrivateHost(host)) {
            throw invalid("사내 LLM 주소는 로컬·사설망 또는 .internal/.local 호스트여야 합니다.");
        }
        return new AiProviderProfile(provider, baseUrl, model, embeddingModel, timeoutSeconds, externalAllowed);
    }

    private boolean isPrivateHost(String host) {
        if (host.equals("localhost") || host.equals("::1") || host.endsWith(".internal") || host.endsWith(".local")) return true;
        if (!host.contains(".")) return true;
        String[] parts = host.split("\\.");
        try {
            int first = Integer.parseInt(parts[0]);
            int second = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
            return first == 10 || first == 127 || (first == 192 && second == 168)
                    || (first == 172 && second >= 16 && second <= 31)
                    || host.startsWith("fc") || host.startsWith("fd");
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private ApplicationException invalid(String message) {
        return new ApplicationException("AI_PROVIDER_SETTINGS_INVALID", HttpStatus.BAD_REQUEST, message);
    }
}
