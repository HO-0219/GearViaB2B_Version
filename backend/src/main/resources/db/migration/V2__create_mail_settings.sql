CREATE TABLE IF NOT EXISTS mail_settings (
    id BIGINT NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    username VARCHAR(255) NULL,
    encrypted_password VARCHAR(1000) NULL,
    smtp_auth BOOLEAN NOT NULL,
    starttls BOOLEAN NOT NULL,
    from_address VARCHAR(320) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;
