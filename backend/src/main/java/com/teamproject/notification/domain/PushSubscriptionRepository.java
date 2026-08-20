package com.teamproject.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    List<PushSubscription> findAllByUserId(Long userId);
    long deleteByEndpointAndUserId(String endpoint, Long userId);
    long deleteAllByUserId(Long userId);
}
