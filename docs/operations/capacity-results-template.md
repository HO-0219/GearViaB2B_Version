# GearVia Capacity Result

Status: **UNMEASURED — no supported concurrent-user number is published yet.**

| Topology | CPU / RAM | MySQL | Storage | Concurrent users | p95 | Error rate | Integrity | Recovery | Decision |
|---|---|---|---|---:|---:|---:|---|---|---|
| Single node | TBD | MySQL 8.x, TBD host | Local/NAS TBD | TBD | TBD | TBD | TBD | TBD | Not tested |
| Two app nodes | TBD | External MySQL 8.x | Shared NAS | TBD | TBD | TBD | TBD | TBD | Not tested |

## Release thresholds

- User-facing read p95: at most 1,000 ms (adjust only in an approved test profile)
- HTTP error rate: at most 1%
- Database pool wait/rejections: zero sustained saturation
- Executor rejections: zero for the tested normal workload
- Data/file integrity: all sampled identifiers and checksums match
- Recovery: readiness and p95 return to baseline within the recorded recovery window

Record the exact Git commit, images/digests, runtime configuration checksum, dataset size, test
duration, workload mix, raw JSON/CSV paths, monitoring export, and reviewer sign-off with every row.
