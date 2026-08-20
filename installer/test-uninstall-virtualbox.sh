#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UNINSTALLER="$SCRIPT_DIR/uninstall-virtualbox.sh"
TEST_ROOT="$(mktemp -d)/b2bgearvia"

test -x "$UNINSTALLER"
bash -n "$UNINSTALLER"

output="$({
  B2B_INSTALL_ROOT="$TEST_ROOT" "$UNINSTALLER" --dry-run
} 2>&1)"

for expected in \
  "B2BGearVia VirtualBox removal plan" \
  "Install root: $TEST_ROOT" \
  "containers and network" \
  "b2bgearvia-mysql-data" \
  "b2bgearvia-uploads" \
  "application images" \
  "Git clone: keep" \
  "Docker Engine: keep"; do
  [[ "$output" == *"$expected"* ]] || {
    printf 'missing dry-run output: %s\n%s\n' "$expected" "$output" >&2
    exit 1
  }
done

test ! -e "$TEST_ROOT"

echo "VirtualBox automatic uninstaller contract: OK"
