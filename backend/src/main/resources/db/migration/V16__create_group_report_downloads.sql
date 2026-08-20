CREATE TABLE group_report_downloads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NOT NULL,
    scope ENUM('GROUP', 'MY') NOT NULL,
    period_type ENUM('WEEKLY', 'MONTHLY', 'YEARLY') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_group_report_downloads_limit (group_id, scope, created_at),
    CONSTRAINT fk_group_report_downloads_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_group_report_downloads_member FOREIGN KEY (requested_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
