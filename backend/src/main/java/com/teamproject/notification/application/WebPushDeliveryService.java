package com.teamproject.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.notification.domain.PushSubscription;
import com.teamproject.notification.domain.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Arrays;
import java.util.stream.Collectors;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class WebPushDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(WebPushDeliveryService.class);
    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private final CloseableHttpClient httpClient;
    private PushService pushService;

    public WebPushDeliveryService(PushSubscriptionRepository subscriptions, ObjectMapper objectMapper,
            @Value("${app.notification.push.public-key:}") String publicKey,
            @Value("${app.notification.push.private-key:}") String privateKey,
            @Value("${app.notification.push.subject:mailto:no-reply@b2bgearvia.local}") String subject) {
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.subject = subject.trim();
        this.httpClient = HttpClients.createSystem();
    }

    @PostConstruct
    void initialize() {
        if (publicKey.isBlank() || privateKey.isBlank()) return;
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(publicKey, privateKey, subject);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid VAPID Web Push configuration", exception);
        }
    }

    @PreDestroy
    void closeHttpClient() throws IOException {
        httpClient.close();
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(PushNotificationEvent event) {
        if (pushService == null) {
            log.debug("Web Push is not configured; skipping delivery for user {}", event.userId());
            return;
        }
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsString(new Payload(
                    event.notificationId(), event.notificationType(),
                    event.groupId(), event.taskId(), event.commentId(),
                    event.title(), event.message(), event.targetUrl(), event.tag()))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            log.warn("Could not serialize Web Push payload for user {}", event.userId(), exception);
            return;
        }
        var targets = subscriptions.findAllByUserId(event.userId());
        if (targets.isEmpty()) {
            log.debug("No push subscription registered for user {}", event.userId());
            return;
        }
        for (PushSubscription subscription : targets) {
            send(subscription, payload);
        }
    }

    private void send(PushSubscription subscription, byte[] payload) {
        try (CloseableHttpResponse response = httpClient.execute(request(subscription, payload))) {
            int status = response.getStatusLine().getStatusCode();
            // 거절되었거나 만료된 구독은 남겨 두면 계속 실패하므로 함께 정리한다.
            if (status == 403 || status == 404 || status == 410) {
                log.warn("Dropping push subscription {} after provider status {}: {}",
                        subscription.getId(), status, reason(response));
                subscriptions.deleteById(subscription.getId());
            } else if (status < 200 || status >= 300) {
                log.warn("Web Push provider returned status {} for subscription {}: {}",
                        status, subscription.getId(), reason(response));
            } else {
                log.debug("Web Push delivered to subscription {}", subscription.getId());
            }
        } catch (Exception exception) {
            log.warn("Web Push delivery failed for subscription {}", subscription.getId(), exception);
        } catch (LinkageError error) {
            // web-push가 기대하는 BouncyCastle 버전과 어긋나면 Error로 떨어져 Exception 처리에 걸리지 않는다.
            log.error("Web Push delivery aborted for subscription {} by a dependency mismatch",
                    subscription.getId(), error);
        }
    }

    private HttpPost request(PushSubscription subscription, byte[] payload) throws Exception {
        HttpPost request = pushService.preparePost(new Notification(subscription.getEndpoint(),
                subscription.getP256dhKey(), subscription.getAuthSecret(), payload), Encoding.AES128GCM);
        // web-push 5.1.2는 이 헤더의 Base64URL 값에 패딩을 붙이지만 FCM은 패딩 없는 형식만 허용한다.
        var cryptoKey = request.getFirstHeader("Crypto-Key");
        if (cryptoKey != null) request.setHeader("Crypto-Key", normalizeCryptoKey(cryptoKey.getValue()));
        return request;
    }

    static String normalizeCryptoKey(String value) {
        return Arrays.stream(value.split(";", -1))
                .map(part -> part.replaceFirst("=+$", ""))
                .collect(Collectors.joining(";"));
    }

    // 푸시 제공자는 거절 사유를 본문에 담아 보낸다. 이게 없으면 상태 코드만으로 원인을 좁힐 수 없다.
    private String reason(HttpResponse response) {
        try {
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            return body.isBlank() ? "no response body" : body.strip();
        } catch (Exception exception) {
            return "unreadable response body";
        }
    }

    private record Payload(Long notificationId, String notificationType,
            Long groupId, Long taskId, Long commentId,
            String title, String body, String url, String tag) {}
}
