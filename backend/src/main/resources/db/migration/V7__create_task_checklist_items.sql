CREATE TABLE task_checklist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    content VARCHAR(300) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by_member_id BIGINT NULL,
    completed_at DATETIME(6) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_task_checklist_items_task_sort (task_id, sort_order, id),
    CONSTRAINT fk_checklist_items_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_checklist_items_completed_by FOREIGN KEY (completed_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
