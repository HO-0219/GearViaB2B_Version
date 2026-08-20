package com.teamproject.chat.application;

import com.teamproject.chat.application.dto.ChatDtos.MessageResponse;

public record ChatMessageEvent(Long channelId, MessageResponse message) {}
