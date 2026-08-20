CREATE TABLE comment_mentions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    mentioned_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_mentions_comment_member (comment_id, mentioned_member_id),
    INDEX idx_comment_mentions_member_created (mentioned_member_id, created_at, id),
    CONSTRAINT fk_comment_mentions_comment FOREIGN KEY (comment_id) REFERENCES task_comments (id),
    CONSTRAINT fk_comment_mentions_member FOREIGN KEY (mentioned_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
