# GearVia On-Premise Stages 7-10 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver safe NAS cutover, personal MCP access, an OpenAI-compatible internal LLM option, and production Ubuntu lifecycle and capacity tooling.

**Architecture:** Keep the Spring Boot modular monolith. Infrastructure changes use persistent jobs and verified cutover; MCP authenticates independently with hashed personal tokens but reuses application authorization services; AI clients use a runtime-selected provider profile; Ubuntu scripts only manage fixed GearVia paths and services.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Flyway, MySQL 8.x, React, TypeScript, Bash, Maven, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-02-gearvia-agent-onprem-operations-design.md`

## Global Constraints

- Support Ubuntu Server 24.04 LTS x86_64 and MySQL 8.x only.
- MCP is enabled only for configured intranet or VPN CIDRs; public Internet exposure is unsupported.
- Store only strong hashes of personal MCP tokens and show plaintext once.
- Reuse existing application services and membership authorization; expose no SQL, shell, filesystem, or generic HTTP tool.
- Require a GearVia approval record before every high-risk mutation.
- Keep all pages, request bodies, responses, queues, timeouts, and concurrency bounded.
- Never log credentials, plaintext tokens, document content, or infrastructure secrets.
- Write a failing behavioral test before production code and commit each stage independently.

---

## Stage 7: Verified NAS Migration and Rollback

**Files:**
- Create: `backend/src/main/java/com/teamproject/resource/storage/NasMigrationService.java`
- Modify: `backend/src/main/java/com/teamproject/resource/storage/DynamicFileStorage.java`
- Modify: `backend/src/main/java/com/teamproject/admin/application/AdminStorageSettingsService.java`
- Modify: `backend/src/main/java/com/teamproject/admin/presentation/AdminStorageSettingsController.java`
- Test: `backend/src/test/java/com/teamproject/resource/storage/NasMigrationServiceTest.java`
- Test: `backend/src/test/java/com/teamproject/resource/storage/DynamicFileStorageTest.java`
- Modify: `docs/superpowers/status/gearvia-agent-onprem-status.md`

**Interfaces:**
- Produces: `NasMigrationService.preflight(): NasPreflight` with source count/bytes, required bytes, target free bytes, mount identity, and success.
- Produces: `NasMigrationService.migrateAndVerify(): MigrationResult` that copies bounded batches, rereads bytes and content types, and switches only after verification.
- Produces: `NasMigrationService.rollback(): MigrationResult` that restores the previous provider without deleting either copy.

- [ ] Write tests proving path containment, insufficient-space rejection, byte/content-type verification, no switch on copy failure, successful switch, and rollback.
- [ ] Run `cd backend && ./mvnw -Dtest=NasMigrationServiceTest,DynamicFileStorageTest test` and confirm the new tests fail because the service contract is absent.
- [ ] Implement preflight, bounded copy/verify, atomic provider selection, and non-destructive rollback; keep file I/O outside business transactions.
- [ ] Run the focused tests and the storage/admin security regression suite.
- [ ] Update status and commit with `feat: add verified NAS migration workflow`.

## Stage 8: Personal MCP Tokens and Agent Gateway

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_mcp_gateway_tables.sql`
- Create: `backend/src/main/java/com/teamproject/mcp/domain/McpPersonalToken.java`
- Create: `backend/src/main/java/com/teamproject/mcp/domain/McpPersonalTokenRepository.java`
- Create: `backend/src/main/java/com/teamproject/mcp/domain/McpToolCallAudit.java`
- Create: `backend/src/main/java/com/teamproject/mcp/domain/McpToolCallAuditRepository.java`
- Create: `backend/src/main/java/com/teamproject/mcp/application/McpTokenService.java`
- Create: `backend/src/main/java/com/teamproject/mcp/application/McpToolCatalog.java`
- Create: `backend/src/main/java/com/teamproject/mcp/presentation/McpTokenController.java`
- Create: `backend/src/main/java/com/teamproject/mcp/presentation/McpGatewayController.java`
- Create: `backend/src/main/java/com/teamproject/mcp/security/McpNetworkPolicy.java`
- Create: `backend/src/main/java/com/teamproject/mcp/security/McpAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/teamproject/authorization/config/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `infra/b2b/runtime.env.example`
- Create: `frontend/src/api/mcpApi.ts`
- Modify: `frontend/src/features/user/pages/ProfilePage.tsx`
- Test: `backend/src/test/java/com/teamproject/mcp/McpTokenServiceTest.java`
- Test: `backend/src/test/java/com/teamproject/mcp/McpGatewayApiTest.java`
- Test: `frontend/src/features/user/pages/ProfilePage.test.tsx`
- Modify: `docs/superpowers/status/gearvia-agent-onprem-status.md`

**Interfaces:**
- Produces: one-time `gv_mcp_<random>` bearer secrets whose SHA-256 hashes are stored with label, `READ` scope, expiry, last use/IP, client label, and revocation time.
- Produces: authenticated `POST /mcp` JSON-RPC methods `initialize`, `notifications/initialized`, `tools/list`, and `tools/call`.
- Produces initial read-only tools `gearvia_list_groups`, `gearvia_list_tasks`, and `gearvia_get_task`, each backed by existing bounded application services.
- Produces: My Page endpoints `GET/POST /api/v1/me/mcp-tokens` and `DELETE /api/v1/me/mcp-tokens/{id}`.

- [ ] Write token tests proving plaintext is returned once, hashes are stored, labels/expiry are bounded, revocation is immediate, and suspended users cannot authenticate.
- [ ] Run `cd backend && ./mvnw -Dtest=McpTokenServiceTest test` and confirm missing-contract failures.
- [ ] Implement token persistence/service/controller and rerun until green.
- [ ] Write gateway tests proving allowed-CIDR access, denied-source rejection before tools, scope checks, bounded results, foreign-group denial, JSON-RPC errors, audit correlation, and rate/concurrency rejection.
- [ ] Run `cd backend && ./mvnw -Dtest=McpGatewayApiTest test` and confirm missing gateway failures.
- [ ] Implement the Streamable HTTP JSON-RPC boundary and read-only catalog; register MCP authentication before JWT and keep `/mcp` outside browser CORS assumptions.
- [ ] Add My Page token management with a one-time secret warning and Codex/Claude endpoint example; write UI tests first and run `cd frontend && npm test -- --run`.
- [ ] Run backend security regressions, frontend tests/build, migration tests where Docker is available, update status, and commit with `feat: add personal MCP agent gateway`.

## Stage 9: OpenAI-Compatible Internal LLM Provider

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__extend_ai_provider_settings.sql`
- Modify: `backend/src/main/java/com/teamproject/aiprovider/domain/AiProviderSettings.java`
- Modify: `backend/src/main/java/com/teamproject/aiprovider/infrastructure/openai/DynamicOpenAiSettings.java`
- Modify: `backend/src/main/java/com/teamproject/admin/application/AdminAiSettingsService.java`
- Modify: `backend/src/main/java/com/teamproject/admin/presentation/AdminAiSettingsController.java`
- Modify: `backend/src/main/java/com/teamproject/common/config/B2bConfigurationValidator.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `infra/b2b/runtime.env.example`
- Modify: `frontend/src/api/adminApi.ts`
- Modify: `frontend/src/features/admin/pages/AdminAiSettingsPage.tsx`
- Test: `backend/src/test/java/com/teamproject/aiprovider/infrastructure/openai/DynamicOpenAiSettingsTest.java`
- Test: `backend/src/test/java/com/teamproject/admin/application/AdminAiSettingsServiceTest.java`
- Test: `frontend/src/features/admin/pages/AdminAiSettingsPage.test.tsx`
- Modify: `docs/superpowers/status/gearvia-agent-onprem-status.md`

**Interfaces:**
- Produces provider type `OPENAI` or `INTERNAL_OPENAI_COMPATIBLE`, encrypted credential, HTTPS/intranet base URL, independent chat/embedding models, bounded timeout, and `externalAllowed` policy.
- Produces independent chat and embedding connection-test results without returning target credentials or exception text.
- Existing assistant/report gateways continue obtaining clients through `DynamicOpenAiSettings`.

- [ ] Extend tests first for provider selection, URL validation, external-provider prohibition, independent models, masked credentials, safe failures, and persistence reload.
- [ ] Run focused backend tests and confirm failures for absent provider fields and policy.
- [ ] Add the migration/entity/runtime client changes and minimal admin API contract; rerun focused tests.
- [ ] Write UI tests first, then add provider/policy/base URL/model controls and separate chat/embedding test results.
- [ ] Run AI/report/assistant regression tests plus frontend tests/build, update status, and commit with `feat: support internal OpenAI-compatible models`.

## Stage 10: Ubuntu Lifecycle and Capacity Validation

**Files:**
- Create: `install_gearvia_ai_agent_ubuntu.sh`
- Create: `uninstall_gearvia_ai_agent_ubuntu.sh`
- Create: `infra/ubuntu/lib/gearvia-common.sh`
- Create: `infra/ubuntu/test-lifecycle-scripts.sh`
- Create: `infra/capacity/run-capacity-smoke.sh`
- Create: `infra/capacity/workload.env.example`
- Create: `docs/operations/ubuntu-installation.md`
- Create: `docs/operations/capacity-validation.md`
- Create: `docs/operations/capacity-results-template.md`
- Modify: `docs/superpowers/status/gearvia-agent-onprem-status.md`

**Interfaces:**
- Installer supports `--dry-run`, `--config <absolute-file>`, fresh install, safe rerun, and upgrade with state at `/var/lib/gearvia/install-state.env` and last-known-good config under `/var/lib/gearvia/recovery`.
- Uninstaller supports default application removal with data preservation and `--purge --confirm-purge GEARVIA`; destructive targets are fixed absolute paths validated by the shared library.
- Capacity smoke runner accepts a fixed workload file and emits JSON/CSV fields for concurrency, throughput, average/p95/p99 latency, errors, CPU/memory, DB pool, executor queues, and topology.

- [ ] Write shell tests first using a temporary fake root and stubbed `systemctl`, `docker`, and `curl`; prove unsupported OS/architecture rejection, dry-run immutability, rerun safety, preserved uninstall, purge confirmation, and path guards.
- [ ] Run `bash infra/ubuntu/test-lifecycle-scripts.sh` and confirm missing-script failures.
- [ ] Implement shared validation plus installer/uninstaller state transitions without shell interpolation of secrets; rerun tests.
- [ ] Add a capacity smoke runner that refuses to publish a supported-user number unless latency, error, integrity, and recovery gates all pass; provide an unfilled results template rather than estimates.
- [ ] Run ShellCheck when installed, lifecycle tests, `bash -n` on every script, Compose validation, and dry-run examples.
- [ ] Update operating documentation/status and commit with `feat: add Ubuntu lifecycle and capacity tooling`.

## Final Integration Checkpoint

- [ ] Run `cd backend && ./mvnw test` and record counts, failures, skips, and duration.
- [ ] Run MySQL 8.4 Flyway/index/MCP migrations on a Docker-enabled host; do not close the release gate when skipped.
- [ ] Run `cd frontend && npm test -- --run && npm run build`.
- [ ] Run lifecycle, capacity-config, and Compose script tests.
- [ ] Verify no plaintext MCP token or provider credential appears in logs, JSON responses after creation, git diff, or generated examples.
- [ ] Update the English status and verification documents with measured evidence only.
