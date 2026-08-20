#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_FILE="$(mktemp)"
trap 'rm -f "$OUTPUT_FILE"' EXIT

test -f "$SCRIPT_DIR/compose.virtualbox.yml"
test -f "$SCRIPT_DIR/runtime.virtualbox.env.example"

docker compose \
  --env-file "$SCRIPT_DIR/runtime.virtualbox.env.example" \
  -f "$SCRIPT_DIR/compose.yml" \
  -f "$SCRIPT_DIR/compose.virtualbox.yml" \
  config > "$OUTPUT_FILE"

rg -q 'BOOTSTRAP_ADMIN_SECRET_FILE: /run/b2bgearvia-bootstrap/admin.env' "$OUTPUT_FILE"
rg -q 'target: /run/b2bgearvia-bootstrap' "$OUTPUT_FILE"
rg -q 'MYSQL_ROOT_PASSWORD: gearvia' "$OUTPUT_FILE"
test "$(rg -c 'pull_policy: never' "$OUTPUT_FILE")" -eq 2

git -C "$REPOSITORY_ROOT" check-ignore -q --no-index infra/b2b/bootstrap/admin.env
git -C "$REPOSITORY_ROOT" check-ignore -q --no-index infra/b2b/runtime.virtualbox.env

echo "VirtualBox Compose configuration: OK"
