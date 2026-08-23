CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(40) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NULL,
    name VARCHAR(60) NOT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;



CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_refresh_hash UNIQUE (token_hash),
    INDEX idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

ALTER TABLE users ADD COLUMN nickname VARCHAR(30) NOT NULL DEFAULT '사용자';
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500) NULL;
ALTER TABLE users ADD COLUMN status ENUM('ACTIVE', 'SUSPENDED', 'WITHDRAWN') NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

UPDATE users
SET nickname = name,
    updated_at = created_at
WHERE nickname = '사용자';

ALTER TABLE users ADD COLUMN withdrawn_at DATETIME(6) NULL;

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



CREATE TABLE group_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    invited_by_member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_invitations_token_hash UNIQUE (token_hash),
    INDEX idx_group_invitations_group_status (group_id, status),
    INDEX idx_group_invitations_email_status (email, status),
    CONSTRAINT fk_group_invitations_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_group_invitations_inviter FOREIGN KEY (invited_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    requester_member_id BIGINT NOT NULL,
    approver_member_id BIGINT NULL,
    assignee_member_id BIGINT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NULL,
    priority ENUM('LOW', 'NORMAL', 'HIGH', 'URGENT') NOT NULL DEFAULT 'NORMAL',
    status ENUM('REQUESTED', 'TODO', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'REJECTED', 'CANCELLED') NOT NULL,
    start_at DATETIME(6) NULL,
    due_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    hold_reason VARCHAR(500) NULL,
    stop_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_tasks_group_status_due (group_id, status, due_at),
    INDEX idx_tasks_assignee_status_due (assignee_member_id, status, due_at),
    CONSTRAINT fk_tasks_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_tasks_requester FOREIGN KEY (requester_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_tasks_approver FOREIGN KEY (approver_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE task_status_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    from_status ENUM('REQUESTED', 'TODO', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'REJECTED', 'CANCELLED') NULL,
    to_status ENUM('REQUESTED', 'TODO', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'REJECTED', 'CANCELLED') NOT NULL,
    changed_by_member_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_task_status_histories_task_created (task_id, created_at),
    CONSTRAINT fk_task_histories_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_histories_member FOREIGN KEY (changed_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

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

CREATE TABLE task_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    author_member_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_task_comments_task_created (task_id, created_at, id),
    CONSTRAINT fk_task_comments_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_comments_author FOREIGN KEY (author_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

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

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    group_id BIGINT NULL,
    task_id BIGINT NULL,
    comment_id BIGINT NULL,
    type VARCHAR(40) NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_recipient_event (recipient_user_id, event_key),
    INDEX idx_notifications_recipient_created (recipient_user_id, id),
    INDEX idx_notifications_recipient_unread (recipient_user_id, read_at, id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_notifications_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_notifications_comment FOREIGN KEY (comment_id) REFERENCES task_comments (id)
) ENGINE = InnoDB;

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

CREATE INDEX idx_tasks_group_created ON tasks (group_id, created_at, id);

CREATE INDEX idx_tasks_status_due ON tasks (status, due_at, id);

CREATE TABLE group_invite_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_invite_links_token_hash (token_hash),
    INDEX idx_group_invite_links_group_status (group_id, status, created_at),
    CONSTRAINT fk_group_invite_links_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_group_invite_links_creator FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

ALTER TABLE work_groups
    ADD COLUMN join_code VARCHAR(12) NULL;

UPDATE work_groups
SET join_code = UPPER(SUBSTRING(MD5(CONCAT(UUID(), '-', id)), 1, 8))
WHERE type = 'TEAM' AND join_code IS NULL;

CREATE UNIQUE INDEX uk_work_groups_join_code ON work_groups (join_code);

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

ALTER TABLE work_groups
    ADD COLUMN join_code_hash VARCHAR(64) NULL;

UPDATE work_groups
SET join_code_hash = SHA2(UPPER(join_code), 256)
WHERE join_code IS NOT NULL;

CREATE UNIQUE INDEX uk_work_groups_join_code_hash ON work_groups (join_code_hash);

DROP INDEX uk_work_groups_join_code ON work_groups;

ALTER TABLE work_groups
    DROP COLUMN join_code;

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

ALTER TABLE work_groups ADD COLUMN image_url VARCHAR(500) NULL;

ALTER TABLE users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE refresh_tokens
    ADD COLUMN session_id VARCHAR(36) NULL,
    ADD COLUMN client_mode VARCHAR(10) NOT NULL DEFAULT 'WEB',
    ADD COLUMN absolute_expires_at DATETIME(6) NULL;

UPDATE refresh_tokens
SET session_id = UUID(),
    absolute_expires_at = expires_at
WHERE session_id IS NULL OR absolute_expires_at IS NULL;

ALTER TABLE refresh_tokens
    MODIFY session_id VARCHAR(36) NOT NULL,
    MODIFY absolute_expires_at DATETIME(6) NOT NULL,
    ADD INDEX idx_refresh_session (session_id);

ALTER TABLE refresh_tokens
    ADD COLUMN device_id VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN device_name VARCHAR(100) NOT NULL DEFAULT '알 수 없는 기기',
    ADD COLUMN user_agent VARCHAR(500) NOT NULL DEFAULT 'unknown',
    ADD COLUMN ip_address VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD COLUMN last_used_at DATETIME(6) NULL;

UPDATE refresh_tokens
SET created_at = COALESCE(created_at, DATE_SUB(expires_at, INTERVAL 14 DAY)),
    last_used_at = COALESCE(last_used_at, revoked_at, DATE_SUB(expires_at, INTERVAL 14 DAY))
WHERE created_at IS NULL OR last_used_at IS NULL;

ALTER TABLE refresh_tokens
    MODIFY created_at DATETIME(6) NOT NULL,
    MODIFY last_used_at DATETIME(6) NOT NULL,
    ADD INDEX idx_refresh_tokens_device (user_id, device_id),
    ADD INDEX idx_refresh_tokens_cleanup (absolute_expires_at);

CREATE TABLE admin_mfa_credentials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    encrypted_secret VARCHAR(1000) NOT NULL,
    recovery_code_hashes TEXT NULL,
    enabled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_mfa_credentials_user UNIQUE (user_id),
    CONSTRAINT fk_admin_mfa_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    http_status INT NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    request_id VARCHAR(80) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_audit_logs_occurred (occurred_at, id),
    INDEX idx_admin_audit_logs_actor (actor_user_id, occurred_at),
    CONSTRAINT fk_admin_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE = InnoDB;

UPDATE work_groups
SET name = CONCAT(LEFT(name, CHAR_LENGTH(name) - CHAR_LENGTH('개인 공간')), '개인 일정'),
    updated_at = CURRENT_TIMESTAMP
WHERE type = 'PERSONAL'
  AND name LIKE '%의 개인 공간';

CREATE TABLE push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    p256dh_key VARCHAR(255) NOT NULL,
    auth_secret VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_subscriptions_endpoint (endpoint(512)),
    INDEX idx_push_subscriptions_user (user_id),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NULL,
    type VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    language VARCHAR(5) NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL,
    metrics_json LONGTEXT NOT NULL,
    ai_summary_json LONGTEXT NULL,
    model VARCHAR(80) NULL,
    prompt_version VARCHAR(30) NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    total_tokens INT NULL,
    failure_code VARCHAR(80) NULL,
    generation_started_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    generated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reports_group_type_period_language_revision
        UNIQUE (group_id, type, period_start, period_end, language, revision),
    CONSTRAINT fk_reports_group
        FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_reports_requested_member
        FOREIGN KEY (requested_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE task_activity_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    actor_member_id BIGINT NULL,
    event_type VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    task_status VARCHAR(20) NOT NULL,
    task_priority VARCHAR(20) NOT NULL,
    assignee_member_id BIGINT NULL,
    task_created_at DATETIME(6) NOT NULL,
    due_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    checklist_total INT NOT NULL,
    checklist_completed INT NOT NULL,
    snapshot_version INT NOT NULL,
    history_complete BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_task_activity_group_occurred (group_id, occurred_at, id),
    INDEX idx_task_activity_task_occurred (task_id, occurred_at, id),
    CONSTRAINT fk_task_activity_task
        FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_activity_group
        FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_task_activity_actor
        FOREIGN KEY (actor_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_task_activity_assignee
        FOREIGN KEY (assignee_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

INSERT INTO task_activity_events (
    task_id, group_id, actor_member_id, event_type, occurred_at,
    task_status, task_priority, assignee_member_id, task_created_at,
    due_at, completed_at, checklist_total, checklist_completed,
    snapshot_version, history_complete
)
SELECT
    task.id,
    task.group_id,
    NULL,
    'BASELINE',
    UTC_TIMESTAMP(6),
    task.status,
    task.priority,
    task.assignee_member_id,
    task.created_at,
    task.due_at,
    task.completed_at,
    (SELECT COUNT(*) FROM task_checklist_items item WHERE item.task_id = task.id),
    (SELECT COUNT(*) FROM task_checklist_items item
        WHERE item.task_id = task.id AND item.completed = TRUE),
    1,
    FALSE
FROM tasks task;

ALTER TABLE tasks
    ADD COLUMN blocker_type VARCHAR(30) NULL AFTER hold_reason,
    ADD COLUMN blocker_next_action_type VARCHAR(30) NULL AFTER blocker_type,
    ADD COLUMN blocker_review_date DATE NULL AFTER blocker_next_action_type;

CREATE TABLE weekly_objectives (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    title VARCHAR(120) NOT NULL,
    position INT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_weekly_objectives_group_week_position
        UNIQUE (group_id, week_start, position),
    INDEX idx_weekly_objectives_group_week (group_id, week_start, id),
    CONSTRAINT fk_weekly_objectives_group
        FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_weekly_objectives_creator
        FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE task_weekly_objective_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    objective_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    linked_by_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_weekly_objective_task_week
        UNIQUE (task_id, week_start),
    INDEX idx_task_weekly_objective_objective (objective_id, task_id),
    CONSTRAINT fk_task_weekly_objective_task
        FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_weekly_objective_objective
        FOREIGN KEY (objective_id) REFERENCES weekly_objectives (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_task_weekly_objective_member
        FOREIGN KEY (linked_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

ALTER TABLE task_activity_events
    ADD COLUMN blocker_type VARCHAR(30) NULL AFTER completed_at,
    ADD COLUMN blocker_next_action_type VARCHAR(30) NULL AFTER blocker_type,
    ADD COLUMN blocker_review_date DATE NULL AFTER blocker_next_action_type,
    ADD COLUMN weekly_objective_id BIGINT NULL AFTER blocker_review_date,
    ADD INDEX idx_task_activity_objective (weekly_objective_id, occurred_at),
    ADD CONSTRAINT fk_task_activity_objective
        FOREIGN KEY (weekly_objective_id) REFERENCES weekly_objectives (id)
        ON DELETE SET NULL;

INSERT INTO task_activity_events (
    task_id, group_id, actor_member_id, event_type, occurred_at,
    task_status, task_priority, assignee_member_id, task_created_at,
    due_at, completed_at, blocker_type, blocker_next_action_type,
    blocker_review_date, weekly_objective_id, checklist_total,
    checklist_completed, snapshot_version, history_complete
)
SELECT
    task.id,
    task.group_id,
    NULL,
    'BASELINE',
    UTC_TIMESTAMP(6),
    task.status,
    task.priority,
    task.assignee_member_id,
    task.created_at,
    task.due_at,
    task.completed_at,
    task.blocker_type,
    task.blocker_next_action_type,
    task.blocker_review_date,
    NULL,
    (SELECT COUNT(*) FROM task_checklist_items item WHERE item.task_id = task.id),
    (SELECT COUNT(*) FROM task_checklist_items item
        WHERE item.task_id = task.id AND item.completed = TRUE),
    2,
    CASE WHEN task.status = 'ON_HOLD' THEN FALSE ELSE TRUE END
FROM tasks task;

ALTER TABLE reports
    ADD COLUMN ai_context_json LONGTEXT NULL AFTER metrics_json,
    ADD COLUMN reference_index_json LONGTEXT NULL AFTER ai_context_json,
    ADD COLUMN evidence_json LONGTEXT NULL AFTER reference_index_json,
    ADD COLUMN editorial_json LONGTEXT NULL AFTER ai_summary_json,
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'LEGACY' AFTER status,
    ADD COLUMN editor_version BIGINT NOT NULL DEFAULT 0 AFTER publication_status,
    ADD COLUMN source_report_id BIGINT NULL AFTER editor_version,
    ADD COLUMN finalized_at DATETIME(6) NULL AFTER generated_at,
    ADD COLUMN finalized_by_member_id BIGINT NULL AFTER finalized_at,
    ADD INDEX idx_reports_series_revision (
        group_id, type, period_start, period_end, language, revision
    ),
    ADD INDEX idx_reports_publication (
        group_id, publication_status, period_end
    ),
    ADD CONSTRAINT fk_reports_source_report
        FOREIGN KEY (source_report_id) REFERENCES reports (id),
    ADD CONSTRAINT fk_reports_finalized_member
        FOREIGN KEY (finalized_by_member_id) REFERENCES group_members (id);

CREATE TABLE ai_weekly_report_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    period_from DATE NOT NULL,
    period_to_exclusive DATE NOT NULL,
    language VARCHAR(8) NOT NULL,
    revision INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    analysis_mode VARCHAR(20) NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    analysis_json LONGTEXT NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    model VARCHAR(120) NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    generated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_weekly_report_revision_group_period_lang_rev
        UNIQUE (group_id, period_from, period_to_exclusive, language, revision)
) ENGINE = InnoDB;

CREATE INDEX idx_ai_weekly_report_revision_source_fingerprint
    ON ai_weekly_report_revision (group_id, period_from, period_to_exclusive, source_fingerprint);

CREATE TABLE scheduled_job_locks (
    name VARCHAR(80) NOT NULL,
    locked_until DATETIME(6) NOT NULL,
    locked_at DATETIME(6) NULL,
    locked_by VARCHAR(120) NULL,
    PRIMARY KEY (name)
) ENGINE = InnoDB;

INSERT INTO scheduled_job_locks (name, locked_until)
VALUES ('task-due-reminder', '1970-01-01 00:00:00');

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

CREATE TABLE project_issue_nodes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    assignee_member_id BIGINT NULL,
    created_by_member_id BIGINT NOT NULL,
    level ENUM('MAJOR', 'MIDDLE', 'ISSUE') NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NULL,
    status ENUM('OPEN', 'IN_PROGRESS', 'BLOCKED', 'DONE') NOT NULL DEFAULT 'OPEN',
    sort_order INT NOT NULL DEFAULT 0,
    due_date DATE NULL,
    archived_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_project_issue_nodes_project_parent (project_id, parent_id, archived_at, sort_order, id),
    INDEX idx_project_issue_nodes_assignee (assignee_member_id, status, archived_at),
    CONSTRAINT fk_project_issue_nodes_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_issue_nodes_parent FOREIGN KEY (parent_id) REFERENCES project_issue_nodes (id),
    CONSTRAINT fk_project_issue_nodes_assignee FOREIGN KEY (assignee_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_project_issue_nodes_created_by FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE project_issue_checklist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by_member_id BIGINT NULL,
    completed_at DATETIME(6) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_project_issue_checklist_order (issue_id, sort_order, id),
    CONSTRAINT fk_project_issue_checklist_issue FOREIGN KEY (issue_id) REFERENCES project_issue_nodes (id),
    CONSTRAINT fk_project_issue_checklist_completed_by FOREIGN KEY (completed_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE project_issue_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    uploaded_by_member_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_issue_images_storage_key (storage_key),
    UNIQUE KEY uk_project_issue_images_checksum (issue_id, checksum_sha256),
    INDEX idx_project_issue_images_order (issue_id, sort_order, id),
    CONSTRAINT fk_project_issue_images_issue FOREIGN KEY (issue_id) REFERENCES project_issue_nodes (id),
    CONSTRAINT fk_project_issue_images_uploaded_by FOREIGN KEY (uploaded_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE project_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    issue_node_id BIGINT NULL,
    created_by_member_id BIGINT NOT NULL,
    document_type ENUM('LINK', 'FILE') NOT NULL,
    title VARCHAR(160) NOT NULL,
    external_url VARCHAR(1000) NULL,
    storage_key VARCHAR(500) NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(120) NULL,
    size_bytes BIGINT NULL,
    checksum_sha256 CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_documents_storage_key (storage_key),
    INDEX idx_project_documents_location (project_id, issue_node_id, deleted_at, created_at, id),
    INDEX idx_project_documents_checksum (project_id, issue_node_id, checksum_sha256, deleted_at),
    CONSTRAINT fk_project_documents_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_documents_issue FOREIGN KEY (issue_node_id) REFERENCES project_issue_nodes (id),
    CONSTRAINT fk_project_documents_created_by FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE chat_channels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    issue_node_id BIGINT NULL,
    created_by_member_id BIGINT NOT NULL,
    channel_key VARCHAR(80) NOT NULL,
    name VARCHAR(80) NOT NULL,
    channel_type ENUM('GENERAL', 'TOPIC') NOT NULL,
    archived_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_channels_group_key (group_id, channel_key),
    INDEX idx_chat_channels_group_active (group_id, archived_at, created_at, id),
    CONSTRAINT fk_chat_channels_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_chat_channels_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_chat_channels_issue FOREIGN KEY (issue_node_id) REFERENCES project_issue_nodes (id),
    CONSTRAINT fk_chat_channels_created_by FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_id BIGINT NOT NULL,
    sender_member_id BIGINT NOT NULL,
    message_type ENUM('TEXT', 'FILE', 'IMAGE') NOT NULL,
    content VARCHAR(4000) NULL,
    storage_key VARCHAR(500) NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(120) NULL,
    size_bytes BIGINT NULL,
    checksum_sha256 CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_messages_storage_key (storage_key),
    INDEX idx_chat_messages_channel_created (channel_id, created_at, id),
    INDEX idx_chat_messages_retention (created_at, id),
    CONSTRAINT fk_chat_messages_channel FOREIGN KEY (channel_id) REFERENCES chat_channels (id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE chat_socket_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_socket_tickets_hash (token_hash),
    INDEX idx_chat_socket_tickets_expiry (expires_at, consumed_at),
    CONSTRAINT fk_chat_socket_tickets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

ALTER TABLE tasks
    ADD COLUMN project_id BIGINT NULL AFTER group_id,
    ADD COLUMN project_topic_id BIGINT NULL AFTER project_id,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at,
    ADD INDEX idx_tasks_project_status (project_id, status, deleted_at),
    ADD INDEX idx_tasks_topic_status (project_topic_id, status, deleted_at),
    ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id),
    ADD CONSTRAINT fk_tasks_project_topic FOREIGN KEY (project_topic_id) REFERENCES project_issue_nodes (id);

CREATE TABLE task_assignee_change_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NOT NULL,
    proposed_assignee_member_id BIGINT NOT NULL,
    reviewed_by_member_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NULL,
    review_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_assignee_change_task_status (task_id, status, created_at),
    INDEX idx_assignee_change_group_status (status, created_at),
    CONSTRAINT fk_assignee_change_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_assignee_change_requested_by FOREIGN KEY (requested_by_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_assignee_change_proposed FOREIGN KEY (proposed_assignee_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_assignee_change_reviewed_by FOREIGN KEY (reviewed_by_member_id) REFERENCES group_members (id)
);

CREATE TABLE emergency_issues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NULL,
    audience VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    image_url VARCHAR(500) NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_emergency_group_status_created (group_id, status, created_at),
    INDEX idx_emergency_project_status_created (project_id, status, created_at),
    CONSTRAINT fk_emergency_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_emergency_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_emergency_created_by FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
);

CREATE TABLE group_resources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    created_by_member_id BIGINT NOT NULL,
    resource_type ENUM('LINK', 'FILE') NOT NULL,
    title VARCHAR(120) NOT NULL,
    external_url VARCHAR(1000) NULL,
    storage_key VARCHAR(500) NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(120) NULL,
    size_bytes BIGINT NULL,
    checksum_sha256 CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_group_resources_group_created (group_id, created_at, id),
    INDEX idx_group_resources_task_created (task_id, created_at, id),
    INDEX idx_group_resources_checksum (group_id, checksum_sha256),
    CONSTRAINT fk_group_resources_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_group_resources_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_group_resources_member FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;


CREATE TABLE report_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    weekly_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    weekly_day ENUM('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY') NULL,
    monthly_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    monthly_day TINYINT NULL,
    language ENUM('KO', 'EN', 'BOTH') NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_report_schedules_group UNIQUE (group_id),
    INDEX idx_report_schedules_active (active, weekly_enabled, monthly_enabled),
    CONSTRAINT fk_report_schedules_group FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_report_schedules_user FOREIGN KEY (recipient_user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE report_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    period_type ENUM('WEEKLY', 'MONTHLY') NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    language ENUM('KO', 'EN') NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    status ENUM('PENDING', 'SENT', 'FAILED') NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_attempt_at DATETIME(6) NULL,
    next_retry_at DATETIME(6) NULL,
    error_code VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_report_deliveries_event UNIQUE (event_key),
    INDEX idx_report_deliveries_status_created (status, created_at),
    CONSTRAINT fk_report_deliveries_schedule FOREIGN KEY (schedule_id) REFERENCES report_schedules (id)
) ENGINE = InnoDB;
CREATE TABLE one_time_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    purpose ENUM('SIGNUP', 'PASSWORD_RESET') NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_one_time_tokens_lookup (email, purpose, created_at)
) ENGINE = InnoDB;
ALTER TABLE users ADD COLUMN force_password_change BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    consent_type VARCHAR(40) NOT NULL,
    policy_version VARCHAR(30) NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    source VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_user_consents_user_type_time (user_id, consent_type, agreed_at),
    CONSTRAINT fk_user_consents_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE branding_settings (
    id BIGINT NOT NULL,
    organization_name VARCHAR(80) NULL,
    logo_storage_key VARCHAR(500) NULL,
    logo_content_type VARCHAR(100) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE admin_notices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    recipient_count INT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_notices_status_scheduled (status, scheduled_at),
    CONSTRAINT fk_admin_notices_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB;
