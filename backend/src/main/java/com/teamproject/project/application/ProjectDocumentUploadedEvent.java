package com.teamproject.project.application;

/** Published after a project document file upload commits, so RAG auto-indexing can pick it up. */
public record ProjectDocumentUploadedEvent(Long groupId, Long documentId) {}
