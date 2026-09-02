#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
runner="$repo_root/infra/capacity/run-capacity-smoke.sh"
[[ -x "$runner" ]] || { echo "capacity runner missing" >&2; exit 1; }
"$runner" --config "$repo_root/infra/capacity/workload.env.example" --validate-only >/dev/null

invalid="$(mktemp)"
trap 'rm -f -- "$invalid"' EXIT
printf 'BASE_URL=https://gearvia.internal\nCONCURRENCY=0\nREQUESTS=10\n' > "$invalid"
if "$runner" --config "$invalid" --validate-only >/dev/null 2>&1; then
  echo "invalid capacity configuration was accepted" >&2
  exit 1
fi
echo "Capacity configuration tests passed"
