#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
installer="$repo_root/install_gearvia_ai_agent_ubuntu.sh"
uninstaller="$repo_root/uninstall_gearvia_ai_agent_ubuntu.sh"
tmp_root="$(mktemp -d)"
trap 'rm -rf -- "$tmp_root"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_file() { [[ -f "$1" ]] || fail "missing file: $1"; }
assert_absent() { [[ ! -e "$1" ]] || fail "path should be absent: $1"; }

[[ -x "$installer" ]] || fail "installer missing or not executable"
[[ -x "$uninstaller" ]] || fail "uninstaller missing or not executable"

mkdir -p "$tmp_root/etc"
printf 'ID=debian\nVERSION_ID="12"\n' > "$tmp_root/etc/os-release"
if GEARVIA_TEST_ROOT="$tmp_root" "$installer" --dry-run --config "$repo_root/infra/b2b/runtime.env.example" >/dev/null 2>&1; then
  fail "unsupported operating system was accepted"
fi

printf 'ID=ubuntu\nVERSION_ID="24.04"\n' > "$tmp_root/etc/os-release"
if GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_TEST_ARCH="aarch64" "$installer" --dry-run --config "$repo_root/infra/b2b/runtime.env.example" >/dev/null 2>&1; then
  fail "unsupported architecture was accepted"
fi
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$installer" --dry-run --config "$repo_root/infra/b2b/runtime.env.example" >/dev/null
assert_absent "$tmp_root/opt/b2bgearvia"

GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$installer" --config "$repo_root/infra/b2b/runtime.env.example" >/dev/null
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$installer" --config "$repo_root/infra/b2b/runtime.env.example" >/dev/null
assert_file "$tmp_root/opt/b2bgearvia/compose.yml"
assert_file "$tmp_root/etc/gearvia/runtime.env"
assert_file "$tmp_root/var/lib/gearvia/install-state.env"

mkdir -p "$tmp_root/opt/b2bgearvia/data/local"
printf 'preserve-me' > "$tmp_root/opt/b2bgearvia/data/local/file.bin"
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller" >/dev/null
assert_file "$tmp_root/opt/b2bgearvia/data/local/file.bin"
assert_absent "$tmp_root/etc/gearvia/runtime.env"

if GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller" --purge-data --confirm-purge WRONG >/dev/null 2>&1; then
  fail "purge accepted an invalid confirmation"
fi
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller" --purge-data --confirm-purge GEARVIA >/dev/null
assert_absent "$tmp_root/opt/b2bgearvia/data"

echo "Ubuntu lifecycle script tests passed"
