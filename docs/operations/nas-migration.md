# NAS Migration Safety

The current cutover implementation is certified for one backend process only. It takes a fair,
exclusive storage migration lock, waits for in-flight file operations to drain, blocks new file
reads/writes during copy and verification, and publishes the new provider only after persistence
and byte/content-type verification succeed. Rollback copies back under the same lock and retains
both copies.

The NAS reachability probe uses a random `CREATE_NEW` file, synchronous write, read comparison,
and cleanup. It never truncates a fixed or pre-existing path.

Do not run a storage cutover with two backend instances. Before scale-out, replace the JVM lock
with a database/distributed lease shared by all instances, enter organization maintenance mode,
send the scheduled outage notice, drain writes on every node, and run a final catch-up/verification
pass. Keep the old storage read-only until the rollback window closes.
