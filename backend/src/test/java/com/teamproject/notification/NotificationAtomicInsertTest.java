package com.teamproject.notification;

import com.teamproject.TeamProjectApplication;
import com.teamproject.notification.domain.Notification;
import com.teamproject.notification.domain.NotificationRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TeamProjectApplication.class)
class NotificationAtomicInsertTest {
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentSameEventCreatesExactlyOneNotificationWithoutPoisoningTransaction() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User recipient = users.save(new User("notify_" + suffix, "notify_" + suffix + "@example.com",
                "hash", "알림 동시성", true));
        String eventKey = "CONCURRENT:" + suffix;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> insertTogether(recipient.getId(), eventKey, ready, start));
            Future<Integer> second = executor.submit(() -> insertTogether(recipient.getId(), eventKey, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(0, 1);
            assertThat(notifications.findByRecipientIdAndEventKey(recipient.getId(), eventKey)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private int insertTogether(Long recipientId, String eventKey,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return new TransactionTemplate(transactionManager).execute(status -> {
            int inserted = notifications.insertIgnore(recipientId, null, null, null, null,
                    Notification.Type.SECURITY_NEW_DEVICE.name(), eventKey,
                    "동시 알림", "한 번만 저장되어야 합니다.", LocalDateTime.now());
            assertThat(notifications.countByRecipientIdAndReadAtIsNull(recipientId)).isEqualTo(1);
            return inserted;
        });
    }
}
