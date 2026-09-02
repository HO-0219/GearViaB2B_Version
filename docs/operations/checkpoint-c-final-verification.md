# Checkpoint C and Final Local Verification

Verified at: 2026-09-02 KST  
Commit under test: `69d8008` plus this evidence update  
Environment: Windows development host, Java 21.0.10, Spring Boot 3.3.5, Node/Vite local toolchain

## Results

| Gate | Result | Evidence |
|---|---|---|
| Backend full suite | PASS locally | 485 tests, 0 failures, 0 errors, 2 skipped; 58.404 s |
| Frontend suite | PASS | 5 files, 9 tests; 1.40 s |
| Frontend production build | PASS with warning | 97 modules; main JS 551.04 kB (158.48 kB gzip) |
| Ubuntu lifecycle simulation | PASS | OS rejection, dry-run, rerun, preserved removal, confirmation-guarded purge |
| Capacity config validation | PASS | valid template accepted and invalid zero concurrency rejected |
| Bash syntax | PASS | all new lifecycle and capacity scripts parsed by `bash -n` |
| Compose validation | PASS | VirtualBox/B2B merged configuration valid |
| Secret-pattern review | PASS | no generated MCP token or real provider credential found in tracked changes |

## Open release gates

- The two MySQL Testcontainers checks were skipped because Docker Desktop/daemon was unavailable.
  Run Flyway V1-V6, schema assertions, and index-plan checks against a clean MySQL 8.x-compatible
  instance before release.
- No corporate OpenAI-compatible endpoint was supplied. Test chat, structured Responses/tool calls,
  and embeddings against the chosen internal LLM before enabling it for users.
- Capacity remains **UNMEASURED**. The repository intentionally publishes no concurrent-user number.
  Use `infra/capacity/run-capacity-smoke.sh` on production-representative single-node hardware, then
  fill `docs/operations/capacity-results-template.md` only when every gate passes.
- ShellCheck was not installed on the Windows host. Bash syntax and behavioral tests passed, but
  release CI should run ShellCheck on Ubuntu.
- The frontend build reports an existing chunk-size warning above 500 kB. This is not a build
  failure, but route-level code splitting should be measured before high-latency WAN deployment.

## Commands

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --run && npm run build
infra/ubuntu/test-lifecycle-scripts.sh
infra/capacity/test-capacity-config.sh
bash -n install_gearvia_ai_agent_ubuntu.sh uninstall_gearvia_ai_agent_ubuntu.sh \
  infra/ubuntu/lib/gearvia-common.sh infra/ubuntu/test-lifecycle-scripts.sh \
  infra/capacity/run-capacity-smoke.sh infra/capacity/test-capacity-config.sh
infra/b2b/test-virtualbox-config.sh
```
