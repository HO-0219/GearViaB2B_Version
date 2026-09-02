#!/usr/bin/env bash
set -euo pipefail

die() { echo "[capacity] ERROR: $*" >&2; exit 1; }
config=""; validate_only=false; output_dir="$(pwd)/capacity-results"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --config) [[ $# -ge 2 ]] || die "--config requires a file"; config="$2"; shift 2 ;;
    --output-dir) [[ $# -ge 2 ]] || die "--output-dir requires a path"; output_dir="$2"; shift 2 ;;
    --validate-only) validate_only=true; shift ;;
    -h|--help) echo "Usage: $0 --config /absolute/workload.env [--validate-only] [--output-dir /absolute/path]"; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done
[[ "$config" = /* && -r "$config" ]] || die "--config must name a readable absolute file"

while IFS='=' read -r key value; do
  [[ "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || continue
  case "$key" in BASE_URL|ACCESS_TOKEN|ENDPOINT|CONCURRENCY|REQUESTS|P95_LIMIT_MS|MAX_ERROR_PERCENT|INTEGRITY_VERIFIED|RECOVERY_VERIFIED|TOPOLOGY)
    printf -v "$key" '%s' "$value" ;;
  esac
done < "$config"

: "${BASE_URL:?BASE_URL is required}" "${CONCURRENCY:?CONCURRENCY is required}" "${REQUESTS:?REQUESTS is required}"
[[ "$BASE_URL" =~ ^https?://[^[:space:]]+$ ]] || die "BASE_URL must be HTTP(S)"
[[ "${ENDPOINT:-}" == /* ]] || die "ENDPOINT must start with /"
for pair in "CONCURRENCY:${CONCURRENCY}" "REQUESTS:${REQUESTS}" "P95_LIMIT_MS:${P95_LIMIT_MS:-}" "MAX_ERROR_PERCENT:${MAX_ERROR_PERCENT:-}"; do
  name="${pair%%:*}"; value="${pair#*:}"
  [[ "$value" =~ ^[0-9]+$ ]] || die "$name must be a non-negative integer"
done
(( CONCURRENCY >= 1 && CONCURRENCY <= 1000 )) || die "CONCURRENCY must be 1..1000"
(( REQUESTS >= CONCURRENCY && REQUESTS <= 1000000 )) || die "REQUESTS must be between CONCURRENCY and 1000000"
(( P95_LIMIT_MS >= 1 )) || die "P95_LIMIT_MS must be positive"
(( MAX_ERROR_PERCENT <= 100 )) || die "MAX_ERROR_PERCENT must be 0..100"
[[ "${TOPOLOGY:-}" =~ ^[A-Za-z0-9._-]+$ ]] || die "TOPOLOGY is invalid"

if [[ "$validate_only" == true ]]; then echo "Capacity workload configuration is valid"; exit 0; fi
command -v curl >/dev/null 2>&1 || die "curl is required"
[[ -n "${ACCESS_TOKEN:-}" && "$ACCESS_TOKEN" != replace-* ]] || die "a short-lived ACCESS_TOKEN is required"
[[ "$output_dir" = /* ]] || die "--output-dir must be absolute"

tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT
started_ns="$(date +%s%N)"
for ((i=1; i<=REQUESTS; i++)); do
  (
    curl --silent --show-error --output /dev/null --connect-timeout 5 --max-time 120 \
      --header "Authorization: Bearer $ACCESS_TOKEN" \
      --write-out '%{http_code},%{time_total}\n' "$BASE_URL$ENDPOINT" > "$tmp_dir/$i.csv" 2>/dev/null \
      || printf '000,120.000\n' > "$tmp_dir/$i.csv"
  ) &
  if (( i % CONCURRENCY == 0 )); then wait; fi
done
wait
ended_ns="$(date +%s%N)"

awk -F, '{ printf "%.0f\n", $2 * 1000 }' "$tmp_dir"/*.csv | sort -n > "$tmp_dir/latencies"
errors="$(awk -F, '$1 !~ /^2/ { count++ } END { print count+0 }' "$tmp_dir"/*.csv)"
p95_index="$(( (REQUESTS * 95 + 99) / 100 ))"
p99_index="$(( (REQUESTS * 99 + 99) / 100 ))"
p95="$(sed -n "${p95_index}p" "$tmp_dir/latencies")"
p99="$(sed -n "${p99_index}p" "$tmp_dir/latencies")"
average="$(awk '{ total += $1 } END { printf "%.0f", total/NR }' "$tmp_dir/latencies")"
duration_ms="$(( (ended_ns - started_ns) / 1000000 ))"
throughput="$(awk -v count="$REQUESTS" -v millis="$duration_ms" 'BEGIN { if (millis == 0) print 0; else printf "%.2f", count*1000/millis }')"
error_percent="$(( errors * 100 / REQUESTS ))"

supported=false
if (( p95 <= P95_LIMIT_MS && error_percent <= MAX_ERROR_PERCENT )) \
    && [[ "${INTEGRITY_VERIFIED:-false}" == true && "${RECOVERY_VERIFIED:-false}" == true ]]; then
  supported=true
fi

mkdir -p "$output_dir"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
json="$output_dir/capacity-$stamp.json"; csv="$output_dir/capacity-$stamp.csv"
printf '{"measuredAt":"%s","topology":"%s","concurrency":%d,"requests":%d,"throughputRps":%s,"averageMs":%s,"p95Ms":%s,"p99Ms":%s,"errors":%s,"errorPercent":%s,"integrityVerified":%s,"recoveryVerified":%s,"supported":%s,"cpuPercent":null,"memoryBytes":null,"dbPool":null,"executorQueues":null}\n' \
  "$stamp" "$TOPOLOGY" "$CONCURRENCY" "$REQUESTS" "$throughput" "$average" "$p95" "$p99" "$errors" "$error_percent" \
  "${INTEGRITY_VERIFIED:-false}" "${RECOVERY_VERIFIED:-false}" "$supported" > "$json"
printf 'measured_at,topology,concurrency,requests,throughput_rps,average_ms,p95_ms,p99_ms,errors,error_percent,integrity_verified,recovery_verified,supported\n%s,%s,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
  "$stamp" "$TOPOLOGY" "$CONCURRENCY" "$REQUESTS" "$throughput" "$average" "$p95" "$p99" "$errors" "$error_percent" \
  "${INTEGRITY_VERIFIED:-false}" "${RECOVERY_VERIFIED:-false}" "$supported" > "$csv"
echo "Capacity evidence: $json"
[[ "$supported" == true ]] || die "run completed but cannot publish a supported-user number until every gate passes"
