package com.teamproject.group.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GroupInvitation> findByTokenHash(String tokenHash);
    Optional<GroupInvitation> findFirstByGroupIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
            Long groupId, String email, GroupInvitation.Status status);
    Optional<GroupInvitation> findByIdAndGroupId(Long id, Long groupId);
    List<GroupInvitation> findAllByGroupIdOrderByCreatedAtDesc(Long groupId);
}
