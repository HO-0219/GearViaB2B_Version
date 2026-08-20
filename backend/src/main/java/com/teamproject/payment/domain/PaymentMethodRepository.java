package com.teamproject.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    List<PaymentMethod> findAllByUserIdAndStatus(Long userId, PaymentMethod.Status status);
    Optional<PaymentMethod> findByIdAndUserId(Long id, Long userId);
}
