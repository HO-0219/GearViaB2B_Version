package com.teamproject.notification.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.notification.application.dto.PushDtos.PushConfigResponse;
import com.teamproject.notification.application.dto.PushDtos.PushSubscriptionRequest;
import com.teamproject.notification.domain.PushSubscription;
import com.teamproject.notification.domain.PushSubscriptionRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserConsent;
import com.teamproject.user.domain.UserConsentRepository;
import com.teamproject.user.domain.UserRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {
    private static final String CONSENT_POLICY_VERSION = "2026-07-25-v2";
    private static final List<String> PUSH_HOST_SUFFIXES = List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com",
            "notify.windows.com", "wns.windows.com");
    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final UserConsentRepository consents;
    private final String publicKey;
    private final String privateKey;

    public PushSubscriptionService(PushSubscriptionRepository subscriptions, UserRepository users,
            UserConsentRepository consents,
            @Value("${app.notification.push.public-key:}") String publicKey,
            @Value("${app.notification.push.private-key:}") String privateKey) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.consents = consents;
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
    }

    @Transactional(readOnly = true)
    public PushConfigResponse config(Long userId) {
        return new PushConfigResponse(enabled(), enabled() ? publicKey : "", notificationConsent(userId));
    }

    @Transactional
    public void subscribe(Long userId, PushSubscriptionRequest request) {
        if (!enabled()) throw new ApplicationException("PUSH_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                "푸시 알림이 아직 설정되지 않았습니다.");
        validateEndpoint(request.endpoint());
        if (!validKey(request.p256dh(), 65) || !validKey(request.auth(), 16)) {
            throw new ApplicationException("INVALID_PUSH_KEYS", HttpStatus.BAD_REQUEST,
                    "유효하지 않은 푸시 암호화 키입니다.");
        }
        User user = users.findById(userId).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
                .map(existing -> { existing.refresh(user, request.p256dh(), request.auth()); return existing; })
                .orElseGet(() -> new PushSubscription(user, request.endpoint(), request.p256dh(), request.auth()));
        subscriptions.save(subscription);
        if (!notificationConsent(userId)) {
            consents.save(new UserConsent(user, UserConsent.Type.SERVICE_NOTIFICATIONS,
                    CONSENT_POLICY_VERSION, true, "PWA_PUSH_PERMISSION"));
        }
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint, userId);
    }

    @Transactional
    public void unsubscribeAll(Long userId) { subscriptions.deleteAllByUserId(userId); }

    private boolean notificationConsent(Long userId) {
        return consents.findFirstByUserIdAndConsentTypeOrderByAgreedAtDescIdDesc(
                userId, UserConsent.Type.SERVICE_NOTIFICATIONS).map(UserConsent::isAgreed).orElse(false);
    }

    private boolean enabled() { return !publicKey.isBlank() && !privateKey.isBlank(); }

    private void validateEndpoint(String rawEndpoint) {
        try {
            URI endpoint = URI.create(rawEndpoint);
            String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(Locale.ROOT);
            boolean supportedHost = PUSH_HOST_SUFFIXES.stream()
                    .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || !supportedHost) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException("INVALID_PUSH_ENDPOINT", HttpStatus.BAD_REQUEST,
                    "지원하지 않는 푸시 알림 주소입니다.");
        }
    }

    private boolean validKey(String value, int expectedBytes) {
        try {
            return Base64.getUrlDecoder().decode(value).length == expectedBytes;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
