CREATE TABLE group_invite_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_invite_links_token_hash (token_hash),
    INDEX idx_group_invite_links_group_status (group_id, status, created_at),
    CONSTRAINT fk_group_invite_links_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_group_invite_links_creator FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
