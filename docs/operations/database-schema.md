# B2BGearVia database schema

The B2B installation uses the MySQL database named exactly `b2bgearvia`.
The Flyway baseline is `V1__create_b2bgearvia_schema.sql`; it contains only
the retained B2B model, including Web Push subscriptions and
`users.force_password_change`. Payment, billing subscription, social-account,
and public-signup-only tables are not part of a new installation.

After `V1`, every schema change must be shipped as a new, immutable Flyway
migration (`V2`, `V3`, …). Never edit a migration that has been deployed.
The migration test uses the isolated `b2bgearvia_migration` Testcontainers
database and must not connect to the live `b2bgearvia` database.
