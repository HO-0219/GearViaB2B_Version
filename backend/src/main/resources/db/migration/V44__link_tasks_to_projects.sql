ALTER TABLE tasks
    ADD COLUMN project_id BIGINT NULL AFTER group_id,
    ADD COLUMN project_topic_id BIGINT NULL AFTER project_id,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at,
    ADD INDEX idx_tasks_project_status (project_id, status, deleted_at),
    ADD INDEX idx_tasks_topic_status (project_topic_id, status, deleted_at),
    ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id),
    ADD CONSTRAINT fk_tasks_project_topic FOREIGN KEY (project_topic_id) REFERENCES project_issue_nodes (id);
