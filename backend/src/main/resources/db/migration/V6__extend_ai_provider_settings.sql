ALTER TABLE ai_provider_settings
    ADD COLUMN provider_type VARCHAR(40) NULL,
    ADD COLUMN base_url VARCHAR(500) NULL,
    ADD COLUMN chat_model VARCHAR(120) NULL,
    ADD COLUMN embedding_model VARCHAR(120) NULL,
    ADD COLUMN request_timeout_seconds INT NULL,
    ADD COLUMN external_allowed BOOLEAN NULL;
