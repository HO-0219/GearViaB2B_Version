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
password_file="$tmp_root/db-password"
printf '%s' 'LocalDbPassword-2026!' > "$password_file"
export GEARVIA_TEST_IP_CANDIDATES='192.168.56.101'
export GEARVIA_TEST_HOSTNAME='gearvia-test'

printf 'ID=debian\nVERSION_ID="12"\n' > "$tmp_root/etc/os-release"
if GEARVIA_TEST_ROOT="$tmp_root" "$installer" --dry-run --db-password-file "$password_file" >/dev/null 2>&1; then
  fail "unsupported operating system was accepted"
fi

printf 'ID=ubuntu\nVERSION_ID="24.04"\n' > "$tmp_root/etc/os-release"
if GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_TEST_ARCH="aarch64" "$installer" --dry-run --db-password-file "$password_file" >/dev/null 2>&1; then
  fail "unsupported architecture was accepted"
fi

short_password_file="$tmp_root/db-password-short"
printf '%s' 'Short-2026!' > "$short_password_file"   # 11 chars
if GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$installer" --dry-run --db-password-file "$short_password_file" >/dev/null 2>&1; then
  fail "database password shorter than 16 characters was accepted"
fi
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$installer" --dry-run --db-password-file "$password_file" >/dev/null
assert_absent "$tmp_root/opt/b2bgearvia"

GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 \
  "$installer" --db-password-file "$password_file"
assert_file "$tmp_root/opt/b2bgearvia/compose.yml"
assert_file "$tmp_root/etc/gearvia/runtime.env"
assert_file "$tmp_root/var/lib/gearvia/install-state.env"
grep -Eq '^JWT_SECRET=.{43,}$' "$tmp_root/etc/gearvia/runtime.env"
grep -Eq '^ADMIN_MFA_ENCRYPTION_KEY_BASE64=.{43,}$' "$tmp_root/etc/gearvia/runtime.env"
grep -Fq 'MYSQL_APP_PASSWORD=LocalDbPassword-2026!' "$tmp_root/etc/gearvia/runtime.env"
if [[ "$(uname -s)" != MINGW* ]]; then
  [[ "$(stat -c '%a' "$tmp_root/etc/gearvia/runtime.env")" == "600" ]] || fail "runtime configuration is not mode 0600"
fi
if (cd "$tmp_root" && GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 \
  "$installer" --db-password-file db-password >/dev/null 2>&1); then
  fail "relative database password file was accepted on rerun"
fi

cp "$tmp_root/etc/gearvia/runtime.env" "$tmp_root/runtime.env.before-rerun"
printf '%s' 'ReplacementPasswordMustNotWin-2026!' > "$password_file"
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 \
  "$installer" --db-password-file "$password_file" >/dev/null
cmp "$tmp_root/runtime.env.before-rerun" "$tmp_root/etc/gearvia/runtime.env" >/dev/null || fail "rerun replaced runtime secrets"

mkdir -p "$tmp_root/opt/b2bgearvia/data/local"
printf 'preserve-me' > "$tmp_root/opt/b2bgearvia/data/local/file.bin"
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller" >/dev/null
assert_file "$tmp_root/opt/b2bgearvia/data/local/file.bin"
assert_absent "$tmp_root/etc/gearvia/runtime.env"
assert_absent "$tmp_root/etc/gearvia/tls"
assert_file "$tmp_root/var/lib/gearvia/recovery/database.env"
grep -Fq 'MYSQL_APP_PASSWORD=LocalDbPassword-2026!' "$tmp_root/var/lib/gearvia/recovery/database.env"

# A too-short password left in the recovery file must not be replayed: a valid
# provided password wins instead of crashing the backend on every reinstall.
printf 'MYSQL_APP_PASSWORD=short\nMYSQL_ROOT_PASSWORD=irrelevant\n' \
  > "$tmp_root/var/lib/gearvia/recovery/database.env"
printf '%s' 'RecoveredMustRevalidate-2026!' > "$password_file"
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 \
  "$installer" --db-password-file "$password_file" >/dev/null
grep -Fq 'MYSQL_APP_PASSWORD=RecoveredMustRevalidate-2026!' "$tmp_root/etc/gearvia/runtime.env" \
  || fail "installer replayed an invalid recovered database password"
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller" >/dev/null

if GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller" --purge-data --confirm-purge WRONG >/dev/null 2>&1; then
  fail "purge accepted an invalid confirmation"
fi
GEARVIA_TEST_ROOT="$tmp_root" GEARVIA_SKIP_RUNTIME=1 "$uninstaller" --purge-data --confirm-purge GEARVIA >/dev/null
assert_absent "$tmp_root/opt/b2bgearvia/data"

echo "Ubuntu lifecycle script tests passed"
