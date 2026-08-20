CREATE TABLE work_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type ENUM('PERSONAL', 'TEAM') NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    dashboard_visibility ENUM('LEADER_ONLY', 'MEMBERS') NOT NULL DEFAULT 'MEMBERS',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_groups_created_by (created_by),
    CONSTRAINT fk_groups_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE group_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('LEADER', 'MEMBER') NOT NULL,
    status ENUM('ACTIVE', 'LEFT', 'REMOVED') NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_members_group_user UNIQUE (group_id, user_id),
    INDEX idx_group_members_user_status (user_id, status),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

INSERT INTO work_groups (type, name, description, timezone, dashboard_visibility, created_by, created_at, updated_at)
SELECT 'PERSONAL', CONCAT(nickname, '의 개인 공간'), NULL, 'Asia/Seoul', 'MEMBERS', id, created_at, updated_at
FROM users;

INSERT INTO group_members (group_id, user_id, role, status, joined_at)
SELECT g.id, g.created_by, 'LEADER', 'ACTIVE', g.created_at
FROM work_groups g
WHERE g.type = 'PERSONAL';
