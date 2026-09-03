CREATE TABLE deployment_settings (
    id BIGINT NOT NULL,
    public_url VARCHAR(255) NOT NULL,
    certificate_issuer VARCHAR(255) NULL,
    certificate_not_after DATETIME(6) NULL,
    certificate_sans VARCHAR(1024) NULL,
    apply_version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOTSTRAP',
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_deployment_settings_singleton CHECK (id = 1)
) ENGINE = InnoDB;
