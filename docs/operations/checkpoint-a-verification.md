# Checkpoint A Verification

Date: 2026-09-02

## Environment

- Host: Windows 11 x86_64 development workstation
- Java: Oracle JDK 21.0.10 LTS
- Maven: 3.9.11 via Maven Wrapper
- Node.js: 24.14.1
- npm: 11.11.0
- Application database for local suite: H2 in MySQL compatibility mode
- Required integration database: MySQL 8.4 via Testcontainers

## Results

| Verification | Result | Evidence |
|---|---|---|
| Operational saturation | PASS | 1 test, 0 failures/errors, 14.321 s Maven total |
| Backend regression | PASS | 468 tests, 0 failures/errors, 2 skipped, 55.544 s Maven total |
| MySQL 8.4 migration and index plan | NOT EXECUTED | 2 tests skipped because no Docker environment was available |
| Frontend unit tests | PASS | 4 files, 8 tests, 1.52 s |
| Frontend production build | PASS | TypeScript and Vite build completed; existing large-chunk warning remains |
| VirtualBox Compose validation | PASS | Generated Compose configuration reported `OK` |

## Saturation Contract

With the document indexing executor limited to one active task and one queued task, a third
submission was rejected and recorded by executor telemetry. While that executor remained full,
HTTP login, authorized task retrieval, notification executor work, and the public readiness check
all completed successfully. Test latches are released in a `finally` block.

## Commands

```text
cd backend && ./mvnw -Dtest=OperationalSaturationIntegrationTest test
cd backend && ./mvnw test
cd backend && ./mvnw -Dtest=MySqlFlywayMigrationTest,MySqlOperationalIndexTest test
cd frontend && npm test -- --run
cd frontend && npm run build
bash infra/b2b/test-virtualbox-config.sh
```

## Gate Decision

The local Checkpoint A gate passes. Release qualification remains open until the two Testcontainers
tests pass against MySQL 8.4 on a Docker-enabled integration host. No MySQL version or execution-plan
claim is inferred from the skipped tests.
