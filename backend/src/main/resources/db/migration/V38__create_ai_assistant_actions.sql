CREATE TABLE ai_assistant_actions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    tool_name VARCHAR(40) NOT NULL,
    arguments_json JSON NOT NULL,
    summary VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    executed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_ai_assistant_actions_user_created (user_id, created_at, id),
    INDEX idx_ai_assistant_actions_expiry (status, expires_at),
    CONSTRAINT fk_ai_assistant_actions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_assistant_actions_group FOREIGN KEY (group_id) REFERENCES work_groups (id)
) ENGINE = InnoDB;
