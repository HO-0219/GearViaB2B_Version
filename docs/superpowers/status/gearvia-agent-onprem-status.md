# GearVia Agent and On-Premise Delivery Status

Last updated: 2026-09-02

## Source Documents

- Design: `docs/superpowers/specs/2026-09-02-gearvia-agent-onprem-operations-design.md`
- Active plan: `docs/superpowers/plans/2026-09-02-gearvia-onprem-checkpoint-a.md`

## Stage Status

| Stage | Scope | Status | Evidence |
|---|---|---|---|
| 1 | Operational configuration and job foundations | Implemented; MySQL verification pending | Tasks 1-2 focused tests passed |
| 2 | API, database, pool, and concurrency optimization | In progress; MySQL EXPLAIN pending | Task 3 bounded queries verified locally |
| 3 | Executor isolation, health, telemetry, and alerts | Implemented | Tasks 4-6 focused tests passed |
| A | Checkpoint A integration verification | Local gate passed; MySQL verification pending | Tasks 1-7 integrated locally |
| 4 | Organization-wide notices and maintenance mode | Not started | - |
| 5 | External MySQL preflight | Not started | - |
| 6 | MySQL migration and rollback | Not started | - |
| B | Checkpoint B integration verification | Not started | - |
| 7 | NAS migration and rollback | Implemented locally | Verified copy, switch, and non-destructive rollback tests |
| 8 | Personal MCP tokens and Agent Gateway | Implemented; MySQL verification pending | Hashed tokens, intranet policy, read tools, audit, limits, and My Page UI |
| 9 | Internal LLM provider | Implemented locally; live endpoint verification pending | Runtime provider, egress policy, chat/embedding models, and admin UI |
| C | Checkpoint C integration verification | Local gate passed; environment gates pending | Stage 7-10 integration and full regression passed |
| 10 | Ubuntu lifecycle scripts and capacity validation | Implemented locally | Safe rerun/removal tests, syntax gates, and evidence-only capacity runner |
| Final | Full integration and capacity matrix | Local regression passed; capacity unmeasured | 485 backend and 9 frontend tests passed; no capacity claim |

## Completed Commits

- `875909c` - architecture design
- `9b6edbf` - design formatting correction
- `7a442df` - remove calendar test date dependency
- `f81e7fe` - validated runtime tuning limits and instance identity
- `f52ab75` - persistent infrastructure-change state machine
- `96f2d8b` - bounded database-side task filtering and MySQL index-plan gate
- `4520da2` - bounded named executors and saturation telemetry
- `4e14cc8` - dependency readiness and internal metrics
- `bac0742` - administrator operational telemetry and alerts

## Verification Evidence

- Baseline backend suite: 446 tests, 0 failures, 0 errors, 1 Docker-dependent skip.
- Task 1 focused suite: `B2BGearViaApplicationTest`, `RuntimeTuningPropertiesTest`, and
  `B2bConfigurationValidatorTest` passed (19 tests, 0 failures/errors).
- `infra/b2b/test-virtualbox-config.sh`: passed; generated Compose configuration is valid.
- Task 2 focused suite: infrastructure state transitions and application mappings passed
  (6 tests passed); MySQL 8.4 Testcontainers test was skipped because Docker is unavailable.
- Task 3 focused suite: 13 tests passed with 0 failures/errors; 2 MySQL Testcontainers
  tests skipped because Docker is unavailable.
- Task 1-3 backend regression suite: 457 tests, 0 failures, 0 errors, 2 Docker-dependent skips.
- V4 intentionally adds no speculative index. `MySqlOperationalIndexTest` seeds 10,000 tasks
  and requires MySQL to select the four existing operational indexes before Checkpoint A can close.
- Task 4 focused suite: 5 tests, 0 failures/errors. Document-index saturation did not
  consume notification capacity; queue/rejection metrics and explicit async routing were verified.
- Task 5 focused/security suite: 29 tests, 0 failures/errors. Public readiness stayed
  redacted, only the active storage provider affected readiness, and Actuator MVC mappings
  remained isolated from the application API mapping tests.
- Task 6 focused suite: 6 backend tests and 3 frontend tests passed with 0 failures/errors;
  the production frontend TypeScript/Vite build passed. The admin view now shows instance,
  bounded query settings, database pool, dependencies, executor queues, and deterministic alerts.
- Task 4-6 integration suite: 467 backend tests, 0 failures, 0 errors, and 2
  Docker-dependent skips; 8 frontend tests passed and the production frontend build passed.
- The VirtualBox Compose configuration remained valid. A scheduler-boundary regression was
  isolated from the persistent job lock so the notification idempotency test no longer depends
  on whether the full suite crosses a 15-minute cron boundary.
- Task 7 saturation test passed: document-index rejection was contained while HTTP login,
  task retrieval, notification execution, and readiness remained available.
- Checkpoint A local suite: 468 backend tests, 0 failures/errors, 2 Docker-dependent skips;
  8 frontend tests and the production build passed; Compose validation passed.
- Detailed environment, duration, command, and gate evidence is recorded in
  `docs/operations/checkpoint-a-verification.md`.
- Stage 7 NAS workflow preflights source size, reserved target capacity, and mount identity;
  verifies copied bytes and content types before switching; and retains both copies on rollback.
- Stage 7 focused storage suite: 17 tests, 0 failures/errors.
- Stage 8 MCP backend suite: 5 tests, 0 failures/errors. It verifies one-time token issuance,
  hash-only storage, immediate revocation, protocol initialization, bounded read tools,
  CIDR/Origin rejection, audit creation, and per-token rate limiting.
- Stage 8 My Page UI test passed and the production frontend build completed. Registration
  guidance is in `docs/operations/mcp-agent-access.md`.
- Stage 9 policy/settings suite passed together with authentication regression tests. The
  frontend production build passed. Live inference remains an environment acceptance test
  because no corporate LLM endpoint was supplied.
- Stage 10 lifecycle tests pass for Ubuntu rejection, dry-run immutability, rerun safety,
  data-preserving uninstall, guarded purge, and capacity configuration validation.
- Final local suite: 485 backend tests, 0 failures/errors, 2 Docker-dependent skips;
  9 frontend tests passed and the production build completed. Lifecycle, Bash syntax,
  capacity configuration, and Compose gates passed. Details are recorded in
  `docs/operations/checkpoint-c-final-verification.md`.

## Known Issues

- The local Windows environment does not currently provide Poppler or the Python `pypdf` package; the existing installation PDF was not used as an implementation source.
- Docker Desktop/daemon is not running in the current Windows environment, so MySQL 8.4
  migration and index-plan verification remains mandatory on an integration host.

## Next Action

Run MySQL 8.x, live internal-LLM, and measured capacity acceptance gates on the target
integration environment. Do not publish concurrent-user limits before those gates pass.
MySQL 8.4 migration verification remains gated on a Docker-enabled integration host.
