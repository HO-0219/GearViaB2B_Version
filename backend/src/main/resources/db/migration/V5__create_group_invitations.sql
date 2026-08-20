CREATE TABLE group_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    invited_by_member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_invitations_token_hash UNIQUE (token_hash),
    INDEX idx_group_invitations_group_status (group_id, status),
    INDEX idx_group_invitations_email_status (email, status),
    CONSTRAINT fk_group_invitations_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_group_invitations_inviter FOREIGN KEY (invited_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
