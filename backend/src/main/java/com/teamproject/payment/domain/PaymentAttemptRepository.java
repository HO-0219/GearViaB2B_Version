package com.teamproject.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<PaymentAttempt> findByIdAndUserId(Long id, Long userId);
    Optional<PaymentAttempt> findByBusinessKey(String businessKey);
    Page<PaymentAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(PaymentAttempt.Status status);
    List<PaymentAttempt> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            PaymentAttempt.Status status, LocalDateTime updatedBefore, Pageable pageable);
}
