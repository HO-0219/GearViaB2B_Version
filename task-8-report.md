# Task 8 report

- Replaced the legacy V1–V45 chain with the new-install-only B2B V1 baseline.
- Removed payment/billing and social-account schema; retained runtime recovery,
  consent, and Web Push tables, plus forced-password-change state.
- Updated the MySQL 8.4 migration test for `b2bgearvia_migration`, Hibernate
  validation, and idempotent reruns.
- Focused Testcontainers execution depends on Docker availability.
