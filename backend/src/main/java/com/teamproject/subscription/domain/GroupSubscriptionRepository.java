package com.teamproject.subscription.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface GroupSubscriptionRepository extends JpaRepository<GroupSubscription, Long> {
    Optional<GroupSubscription> findByGroupId(Long groupId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroupSubscription s where s.group.id = :groupId")
    Optional<GroupSubscription> findByGroupIdForUpdate(@Param("groupId") Long groupId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroupSubscription s where s.id = :subscriptionId")
    Optional<GroupSubscription> findByIdForUpdate(@Param("subscriptionId") Long subscriptionId);
    List<GroupSubscription> findAllByStatusInAndCurrentPeriodEndLessThanEqual(
            List<GroupSubscription.Status> statuses, LocalDateTime now);
    @Query("select s.id from GroupSubscription s where s.status in ('ACTIVE', 'PAST_DUE') "
            + "and s.nextBillingAt <= :now and s.billingClaimKey is null order by s.nextBillingAt, s.id")
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);
    List<GroupSubscription> findAllByStatusAndPastDueSinceLessThanEqual(
            GroupSubscription.Status status, LocalDateTime deadline);
}
