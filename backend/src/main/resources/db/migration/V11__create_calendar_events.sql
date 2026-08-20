CREATE TABLE calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    location VARCHAR(300) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_calendar_events_group_range (group_id, start_at, end_at, id),
    CONSTRAINT fk_calendar_events_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_calendar_events_creator FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
