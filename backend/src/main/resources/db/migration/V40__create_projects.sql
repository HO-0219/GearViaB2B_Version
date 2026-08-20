CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    lead_member_id BIGINT NULL,
    created_by_member_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    status ENUM('PLANNED', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED') NOT NULL,
    start_date DATE NULL,
    due_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_projects_group_status_updated (group_id, status, updated_at, id),
    CONSTRAINT fk_projects_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_projects_lead_member FOREIGN KEY (lead_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_projects_created_by_member FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
