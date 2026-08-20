ALTER TABLE work_groups
    ADD COLUMN join_code_hash VARCHAR(64) NULL;

UPDATE work_groups
SET join_code_hash = SHA2(UPPER(join_code), 256)
WHERE join_code IS NOT NULL;

CREATE UNIQUE INDEX uk_work_groups_join_code_hash ON work_groups (join_code_hash);

DROP INDEX uk_work_groups_join_code ON work_groups;

ALTER TABLE work_groups
    DROP COLUMN join_code;
