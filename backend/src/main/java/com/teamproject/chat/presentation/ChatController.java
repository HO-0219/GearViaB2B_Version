package com.teamproject.chat.presentation;

import com.teamproject.chat.application.*;
import com.teamproject.chat.application.dto.ChatDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ChatController {
    private final ChatService chat;
    private final ChatSocketTicketService tickets;
    public ChatController(ChatService chat, ChatSocketTicketService tickets) { this.chat = chat; this.tickets = tickets; }
    @GetMapping("/groups/{groupId}/chat/channels")
    List<ChannelResponse> channels(Authentication auth, @PathVariable Long groupId) {
        return chat.channels((Long) auth.getPrincipal(), groupId);
    }
    @PostMapping("/groups/{groupId}/chat/channels")
    @ResponseStatus(HttpStatus.CREATED)
    ChannelResponse createChannel(Authentication auth, @PathVariable Long groupId,
            @Valid @RequestBody CreateChannelRequest request) {
        return chat.createChannel((Long) auth.getPrincipal(), groupId, request);
    }
    @GetMapping("/chat/channels/{channelId}/messages")
    MessagePageResponse messages(Authentication auth, @PathVariable Long channelId,
            @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "50") int limit) {
        return chat.history((Long) auth.getPrincipal(), channelId, beforeId, limit);
    }
    @PostMapping("/chat/channels/{channelId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse send(Authentication auth, @PathVariable Long channelId,
            @Valid @RequestBody SendTextRequest request) {
        return chat.sendText((Long) auth.getPrincipal(), channelId, request.content());
    }
    @PostMapping(path = "/chat/channels/{channelId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse upload(Authentication auth, @PathVariable Long channelId,
            @RequestParam(required = false) String caption, @RequestPart MultipartFile file) {
        return chat.upload((Long) auth.getPrincipal(), channelId, caption, file);
    }
    @GetMapping("/chat/messages/{messageId}/content")
    ResponseEntity<byte[]> content(Authentication auth, @PathVariable Long messageId) {
        var value = chat.download((Long) auth.getPrincipal(), messageId);
        ContentDisposition disposition = value.contentType().startsWith("image/") ? ContentDisposition.inline()
                .filename(value.filename(), StandardCharsets.UTF_8).build() : ContentDisposition.attachment()
                .filename(value.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(value.content());
    }
    @PostMapping("/chat/socket-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    SocketTicketResponse socketTicket(Authentication auth) { return tickets.issue((Long) auth.getPrincipal()); }
}
