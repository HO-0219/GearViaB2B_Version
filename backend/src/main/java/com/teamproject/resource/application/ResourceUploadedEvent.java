package com.teamproject.resource.application;

/** Published after a group resource file upload commits, so RAG auto-indexing can pick it up. */
public record ResourceUploadedEvent(Long groupId, Long resourceId) {}
