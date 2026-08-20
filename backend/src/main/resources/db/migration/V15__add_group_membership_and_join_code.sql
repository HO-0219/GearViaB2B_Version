ALTER TABLE work_groups
    ADD COLUMN membership_plan ENUM('FREE', 'PAID') NOT NULL DEFAULT 'FREE',
    ADD COLUMN join_code VARCHAR(12) NULL;

UPDATE work_groups
SET join_code = UPPER(SUBSTRING(MD5(CONCAT(UUID(), '-', id)), 1, 8))
WHERE type = 'TEAM' AND join_code IS NULL;

CREATE UNIQUE INDEX uk_work_groups_join_code ON work_groups (join_code);
