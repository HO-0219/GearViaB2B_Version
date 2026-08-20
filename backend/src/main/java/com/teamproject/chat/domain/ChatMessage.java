package com.teamproject.chat.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_channel_created", columnList = "channel_id,created_at,id"),
        @Index(name = "idx_chat_messages_retention", columnList = "created_at,id")
})
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "channel_id") private ChatChannel channel;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sender_member_id") private GroupMember sender;
    @Enumerated(EnumType.STRING) @Column(name = "message_type", nullable = false, length = 20) private Type messageType;
    @Column(length = 4000) private String content;
    @Column(length = 500, unique = true) private String storageKey;
    @Column(length = 255) private String originalFilename;
    @Column(length = 120) private String contentType;
    private Long sizeBytes;
    @Column(length = 64, columnDefinition = "char(64)") private String checksumSha256;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    protected ChatMessage() {}
    public static ChatMessage text(ChatChannel channel, GroupMember sender, String content) {
        ChatMessage value = new ChatMessage(channel, sender, Type.TEXT); value.content = content; return value;
    }
    public static ChatMessage attachment(ChatChannel channel, GroupMember sender, Type type, String content,
            String key, String filename, String contentType, long size, String checksum) {
        ChatMessage value = new ChatMessage(channel, sender, type); value.content = content;
        value.storageKey = key; value.originalFilename = filename; value.contentType = contentType;
        value.sizeBytes = size; value.checksumSha256 = checksum; return value;
    }
    private ChatMessage(ChatChannel channel, GroupMember sender, Type type) {
        this.channel = channel; this.sender = sender; this.messageType = type; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public ChatChannel getChannel() { return channel; }
    public GroupMember getSender() { return sender; }
    public Type getMessageType() { return messageType; }
    public String getContent() { return content; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Type { TEXT, FILE, IMAGE }
}
