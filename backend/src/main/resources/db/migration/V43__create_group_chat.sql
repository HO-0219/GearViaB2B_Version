CREATE TABLE chat_channels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    issue_node_id BIGINT NULL,
    created_by_member_id BIGINT NOT NULL,
    channel_key VARCHAR(80) NOT NULL,
    name VARCHAR(80) NOT NULL,
    channel_type ENUM('GENERAL', 'TOPIC') NOT NULL,
    archived_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_channels_group_key (group_id, channel_key),
    INDEX idx_chat_channels_group_active (group_id, archived_at, created_at, id),
    CONSTRAINT fk_chat_channels_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_chat_channels_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_chat_channels_issue FOREIGN KEY (issue_node_id) REFERENCES project_issue_nodes (id),
    CONSTRAINT fk_chat_channels_created_by FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_id BIGINT NOT NULL,
    sender_member_id BIGINT NOT NULL,
    message_type ENUM('TEXT', 'FILE', 'IMAGE') NOT NULL,
    content VARCHAR(4000) NULL,
    storage_key VARCHAR(500) NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(120) NULL,
    size_bytes BIGINT NULL,
    checksum_sha256 CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_messages_storage_key (storage_key),
    INDEX idx_chat_messages_channel_created (channel_id, created_at, id),
    INDEX idx_chat_messages_retention (created_at, id),
    CONSTRAINT fk_chat_messages_channel FOREIGN KEY (channel_id) REFERENCES chat_channels (id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE chat_socket_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_socket_tickets_hash (token_hash),
    INDEX idx_chat_socket_tickets_expiry (expires_at, consumed_at),
    CONSTRAINT fk_chat_socket_tickets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
