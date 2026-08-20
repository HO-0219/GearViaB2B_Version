#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALLER="$SCRIPT_DIR/install-virtualbox.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

test -x "$INSTALLER"
bash -n "$INSTALLER"
rg -q 'command -v curl' "$INSTALLER"
rg -q '^MYSQL_ROOT_PASSWORD=gearvia$' "$INSTALLER"
rg -Fq 'if ! "${COMPOSE[@]}" up -d' "$INSTALLER"
rg -q 'logs --tail=100' "$INSTALLER"

output="$({
  B2B_DRY_RUN=true \
  B2B_INSTALL_ROOT="$TEST_ROOT/opt/b2bgearvia" \
  B2B_VM_IP=192.168.56.101 \
  "$INSTALLER"
} 2>&1)"

for expected in \
  "B2BGearVia VirtualBox automatic installation" \
  "Source repository: " \
  "Install root: $TEST_ROOT/opt/b2bgearvia" \
  "VirtualBox address: 192.168.56.101" \
  "Docker Engine" \
  "Generate runtime secrets" \
  "Create TLS certificate" \
  "Build application images" \
  "Start B2BGearVia" \
  "Wait for application readiness" \
  "AI integration: skipped"; do
  [[ "$output" == *"$expected"* ]] || {
    printf 'missing dry-run output: %s\n%s\n' "$expected" "$output" >&2
    exit 1
  }
done

test ! -e "$TEST_ROOT/opt/b2bgearvia"

echo "VirtualBox automatic installer contract: OK"
