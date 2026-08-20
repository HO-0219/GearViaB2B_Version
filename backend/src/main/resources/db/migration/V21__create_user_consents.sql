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
