CREATE TABLE comment_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    edited_by_member_id BIGINT NOT NULL,
    previous_content VARCHAR(2000) NOT NULL,
    new_content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_revisions_comment
        FOREIGN KEY (comment_id) REFERENCES task_comments (id),
    CONSTRAINT fk_comment_revisions_editor
        FOREIGN KEY (edited_by_member_id) REFERENCES group_members (id)
);

CREATE INDEX idx_comment_revisions_comment_created
    ON comment_revisions (comment_id, created_at);
