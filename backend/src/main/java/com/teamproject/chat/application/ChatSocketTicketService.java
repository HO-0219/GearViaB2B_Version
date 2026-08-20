package com.teamproject.chat.application;

import com.teamproject.chat.application.dto.ChatDtos.SocketTicketResponse;
import com.teamproject.chat.domain.*;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatSocketTicketService {
    private static final long EXPIRES_SECONDS = 60;
    private final ChatSocketTicketRepository tickets;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();
    public ChatSocketTicketService(ChatSocketTicketRepository tickets, UserRepository users) {
        this.tickets = tickets; this.users = users;
    }
    @Transactional
    public SocketTicketResponse issue(Long userId) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.save(new ChatSocketTicket(users.findById(userId).orElseThrow(), hash(raw),
                LocalDateTime.now().plusSeconds(EXPIRES_SECONDS)));
        return new SocketTicketResponse(raw, EXPIRES_SECONDS);
    }
    @Transactional
    public Long consume(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        LocalDateTime now = LocalDateTime.now();
        ChatSocketTicket ticket = tickets.findConsumableForUpdate(hash(raw), now)
                .orElseThrow(this::invalid);
        ticket.consume(now);
        return ticket.getUser().getId();
    }
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void cleanup() { tickets.deleteByExpiresAtBefore(LocalDateTime.now().minusHours(1)); }
    private ApplicationException invalid() { return new ApplicationException(
            "CHAT_SOCKET_TICKET_INVALID", HttpStatus.UNAUTHORIZED, "채팅 연결 정보가 만료되었습니다."); }
    private String hash(String raw) { try { return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
