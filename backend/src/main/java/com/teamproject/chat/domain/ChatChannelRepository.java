package com.teamproject.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ChatChannelRepository extends JpaRepository<ChatChannel, Long> {
    List<ChatChannel> findAllByGroupIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(Long groupId);
    Optional<ChatChannel> findByIdAndArchivedAtIsNull(Long id);
    Optional<ChatChannel> findByGroupIdAndChannelKeyAndArchivedAtIsNull(Long groupId, String channelKey);
    long countByGroupIdAndArchivedAtIsNull(Long groupId);
}
