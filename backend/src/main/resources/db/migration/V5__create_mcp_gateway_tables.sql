CREATE TABLE mcp_personal_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    label VARCHAR(60) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    scope VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL,
    last_ip VARCHAR(64) NULL,
    client_label VARCHAR(60) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_personal_token_hash (token_hash),
    INDEX idx_mcp_personal_user_created (user_id, created_at, id),
    CONSTRAINT fk_mcp_personal_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE mcp_tool_call_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    tool_name VARCHAR(80) NOT NULL,
    target_id VARCHAR(100) NULL,
    source_ip VARCHAR(64) NOT NULL,
    result VARCHAR(20) NOT NULL,
    latency_ms BIGINT NOT NULL,
    instance_id VARCHAR(80) NOT NULL,
    correlation_id VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_mcp_audit_user_created (user_id, created_at, id),
    INDEX idx_mcp_audit_correlation (correlation_id),
    CONSTRAINT fk_mcp_audit_token FOREIGN KEY (token_id) REFERENCES mcp_personal_tokens (id),
    CONSTRAINT fk_mcp_audit_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
