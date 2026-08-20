CREATE TABLE ai_assistant_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    action_id BIGINT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ai_assistant_messages_history (user_id, group_id, id),
    INDEX idx_ai_assistant_messages_cleanup (created_at, id),
    CONSTRAINT fk_ai_assistant_messages_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_assistant_messages_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_ai_assistant_messages_action FOREIGN KEY (action_id)
        REFERENCES ai_assistant_actions (id) ON DELETE SET NULL
) ENGINE = InnoDB;
