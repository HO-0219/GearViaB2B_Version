ALTER TABLE users ADD COLUMN payment_customer_key VARCHAR(100) NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_payment_customer_key UNIQUE (payment_customer_key);

CREATE TABLE payment_methods (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    billing_key_encrypted VARCHAR(1000) NOT NULL,
    issuer_code VARCHAR(20) NULL,
    masked_number VARCHAR(40) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payment_methods_user_status (user_id, status),
    CONSTRAINT fk_payment_methods_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    payment_method_id BIGINT NULL,
    operation_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    order_id VARCHAR(64) NULL,
    amount BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    http_status INT NULL,
    provider_code VARCHAR(100) NULL,
    provider_message VARCHAR(500) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_attempts_idempotency UNIQUE (idempotency_key),
    INDEX idx_payment_attempts_user_created (user_id, created_at),
    CONSTRAINT fk_payment_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_payment_attempts_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id)
) ENGINE = InnoDB;
