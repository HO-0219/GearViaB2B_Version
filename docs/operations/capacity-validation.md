# Capacity Validation

Capacity is certified per hardware/topology, not inferred from CPU count. Keep the internet
blocked and run from an internal load-generator host that is not the GearVia server.

1. Copy `infra/capacity/workload.env.example` outside the repository.
2. Use a dedicated short-lived read-only access token and start with concurrency 10.
3. Run `infra/capacity/run-capacity-smoke.sh --config /absolute/workload.env --output-dir /absolute/evidence`.
4. Repeat at increasing concurrency only after reviewing GearVia monitoring, MySQL pool wait,
   executor queues, host CPU/memory, NAS latency, and error logs.
5. Set `INTEGRITY_VERIFIED=true` only after comparing created/read records and file checksums, and
   set `INTEGRITY_EVIDENCE` to the non-empty evidence file.
6. Set `RECOVERY_VERIFIED=true` only after stopping load and proving queues, pool, readiness, and
   latency return to baseline; set `RECOVERY_EVIDENCE` to that non-empty capture.

The runner writes JSON and CSV, and deliberately reports `supported=false` unless p95 latency,
error percentage, integrity, and recovery gates all pass. CPU, memory, DB-pool, and executor fields
are left null by the portable runner and must be joined from the administrator monitoring export.

Never publish a user limit from a skipped test, localhost-only mock, or a run that used placeholder
tokens. A second application server requires external MySQL, shared NAS, unique `INSTANCE_ID`, a
load balancer, and a separate multi-node test; single-node numbers must not be copied to it.
