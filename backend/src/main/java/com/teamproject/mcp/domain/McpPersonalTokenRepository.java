package com.teamproject.mcp.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpPersonalTokenRepository extends JpaRepository<McpPersonalToken, Long> {
    Optional<McpPersonalToken> findByTokenHash(String tokenHash);
    List<McpPersonalToken> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}
