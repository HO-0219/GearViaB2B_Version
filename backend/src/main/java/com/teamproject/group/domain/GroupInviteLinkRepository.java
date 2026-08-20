package com.teamproject.group.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;

public interface GroupInviteLinkRepository extends JpaRepository<GroupInviteLink, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GroupInviteLink> findByTokenHash(String tokenHash);
    Optional<GroupInviteLink> findByIdAndGroupId(Long id, Long groupId);
    List<GroupInviteLink> findAllByGroupIdAndStatusOrderByCreatedAtDesc(
            Long groupId, GroupInviteLink.Status status);
}
