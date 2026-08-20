package com.teamproject.chat.application;

import com.teamproject.chat.domain.*;
import com.teamproject.group.domain.Group;
import com.teamproject.resource.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatRetentionCleanup {
    private final ChatMessageRepository messages;
    private final FileStorage storage;
    private final int freeDays;
    private final int paidDays;
    public ChatRetentionCleanup(ChatMessageRepository messages, FileStorage storage,
            @Value("${app.features.chat.free-retention-days:10}") int freeDays,
            @Value("${app.features.chat.paid-retention-days:365}") int paidDays) {
        this.messages = messages; this.storage = storage; this.freeDays = freeDays; this.paidDays = paidDays;
    }
    @Scheduled(cron = "${app.features.chat.cleanup-cron:0 35 3 * * *}")
    @Transactional
    public void cleanup() {
        List<String> keys = new ArrayList<>();
        deletePlan(Group.MembershipPlan.FREE, LocalDateTime.now().minusDays(freeDays), keys);
        deletePlan(Group.MembershipPlan.PAID, LocalDateTime.now().minusDays(paidDays), keys);
        if (!keys.isEmpty()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { keys.forEach(storage::delete); }
        });
    }
    private void deletePlan(Group.MembershipPlan plan, LocalDateTime cutoff, List<String> keys) {
        while (true) {
            List<ChatMessage> batch = messages.findByChannelGroupMembershipPlanAndCreatedAtBeforeOrderByIdAsc(
                    plan, cutoff, PageRequest.of(0, 500));
            if (batch.isEmpty()) return;
            batch.stream().map(ChatMessage::getStorageKey).filter(Objects::nonNull).forEach(keys::add);
            messages.deleteAllInBatch(batch);
            if (batch.size() < 500) return;
        }
    }
}
