package com.teamproject.admin.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminNoticeRepository extends JpaRepository<AdminNotice, Long> {
    Page<AdminNotice> findAllByOrderByScheduledAtDesc(Pageable pageable);
    List<AdminNotice> findAllByStatusAndScheduledAtLessThanEqual(AdminNotice.Status status, LocalDateTime cutoff);
    Optional<AdminNotice> findByIdAndStatus(Long id, AdminNotice.Status status);
}
