ALTER TABLE payment_attempts
    ADD COLUMN subscription_id BIGINT NULL AFTER payment_method_id,
    ADD COLUMN business_key VARCHAR(160) NULL AFTER idempotency_key,
    ADD COLUMN billing_period_start DATETIME(6) NULL AFTER business_key,
    ADD COLUMN billing_kind VARCHAR(20) NULL AFTER billing_period_start,
    ADD COLUMN provider_payment_key VARCHAR(200) NULL AFTER order_id;

ALTER TABLE payment_attempts
    ADD CONSTRAINT uk_payment_attempts_business_key UNIQUE (business_key),
    ADD CONSTRAINT fk_payment_attempts_subscription
        FOREIGN KEY (subscription_id) REFERENCES group_subscriptions (id);

CREATE INDEX idx_payment_attempts_subscription_created
    ON payment_attempts (subscription_id, created_at);

ALTER TABLE group_subscriptions
    ADD COLUMN billing_claim_key VARCHAR(160) NULL AFTER next_billing_at,
    ADD COLUMN billing_claimed_at DATETIME(6) NULL AFTER billing_claim_key;

CREATE UNIQUE INDEX uk_group_subscriptions_billing_claim
    ON group_subscriptions (billing_claim_key);

CREATE INDEX idx_group_subscriptions_due_claim
    ON group_subscriptions (status, next_billing_at, billing_claim_key);

INSERT INTO scheduled_job_locks (name, locked_until)
VALUES ('subscription-reconciliation', '1970-01-01 00:00:00');
