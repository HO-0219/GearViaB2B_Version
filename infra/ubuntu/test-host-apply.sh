#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
applier="$repo_root/infra/ubuntu/gearvia-host-apply.sh"
tmp_root="$(mktemp -d)"
trap 'rm -rf -- "$tmp_root"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

[[ -x "$applier" ]] || fail "host apply script missing or not executable"

control="$tmp_root/var/lib/gearvia/control"
tls="$tmp_root/etc/gearvia/tls"
mkdir -p "$control/requests" "$control/results" "$control/candidates" "$tls" \
  "$tmp_root/opt/b2bgearvia" "$tmp_root/etc/gearvia" "$tmp_root/var/lib/gearvia/recovery"

hmac_key="topsecret-host-apply-key-2026"
printf '%s' "$hmac_key" > "$tmp_root/etc/gearvia/host-apply.key"
chmod 0600 "$tmp_root/etc/gearvia/host-apply.key"
: > "$tmp_root/etc/gearvia/runtime.env"
: > "$tmp_root/opt/b2bgearvia/compose.yml"

# Active certificate/key installed before any request.
MSYS2_ARG_CONV_EXCL='/CN=' openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj '/CN=gearvia.corp' \
  -keyout "$tls/privkey.pem" -out "$tls/fullchain.pem" >/dev/null 2>&1
chmod 0600 "$tls/privkey.pem"
active_before="$(sha256sum "$tls/fullchain.pem" | cut -d' ' -f1)"

# Fake docker: "config" and "up" succeed; log calls.
fake_docker="$tmp_root/docker"
cat > "$fake_docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_LOG"
exit 0
EOF
chmod +x "$fake_docker"
export DOCKER_LOG="$tmp_root/docker.log"
export GEARVIA_DOCKER_BIN="$fake_docker"
export GEARVIA_TEST_ROOT="$tmp_root"

sign() {
  printf '%s\n%s\n%s' "$1" "$2" "$3" \
    | openssl dgst -sha256 -hmac "$hmac_key" | sed 's/^.*= //'
}

make_candidate() {
  local dir="$control/candidates/$1"
  mkdir -p "$dir"
  MSYS2_ARG_CONV_EXCL='/CN=' openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj '/CN=new.gearvia.corp' \
    -addext 'subjectAltName=DNS:new.gearvia.corp,DNS:localhost' \
    -keyout "$dir/privkey.pem" -out "$dir/fullchain.pem" >/dev/null 2>&1
  chmod 0600 "$dir/privkey.pem"
}

write_request() {
  local id="$1" url="$2" mode="$3" sig="$4"
  cat > "$control/requests/req.env" <<REQ
requestId=$id
publicUrl=$url
certificateMode=$mode
signature=$sig
REQ
}

run_apply() {
  set +e
  out="$("$applier" "$control/requests/req.env" 2>&1)"
  rc=$?
  set -e
}

assert_code() {
  echo "$out" | grep -Eq "^code=$1$" || fail "expected code=$1, got:\n$out"
}

# 1. Path-escaping request id is rejected before any work.
write_request '../escape' 'https://gearvia.corp' uploaded "$(sign '../escape' 'https://gearvia.corp' uploaded)"
run_apply
assert_code REQUEST_ID_INVALID

# 2. Wrong signature is rejected.
make_candidate 'req-sig'
write_request 'req-sig' 'https://gearvia.corp' uploaded 'deadbeef'
run_apply
assert_code REQUEST_SIGNATURE_INVALID

# 3. Certificate and key that do not match are rejected.
mkdir -p "$control/candidates/req-mismatch"
MSYS2_ARG_CONV_EXCL='/CN=' openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj '/CN=a' \
  -keyout "$tmp_root/other.key" -out "$control/candidates/req-mismatch/fullchain.pem" >/dev/null 2>&1
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$control/candidates/req-mismatch/privkey.pem" >/dev/null 2>&1
chmod 0600 "$control/candidates/req-mismatch/privkey.pem"
write_request 'req-mismatch' 'https://gearvia.corp' uploaded "$(sign 'req-mismatch' 'https://gearvia.corp' uploaded)"
run_apply
assert_code CERTIFICATE_KEY_MISMATCH
[[ "$(sha256sum "$tls/fullchain.pem" | cut -d' ' -f1)" == "$active_before" ]] \
  || fail "active certificate changed on a rejected request"

# 4. Health-check failure rolls the active certificate back unchanged.
make_candidate 'req-health'
write_request 'req-health' 'https://gearvia.corp' uploaded "$(sign 'req-health' 'https://gearvia.corp' uploaded)"
GEARVIA_TEST_HEALTH=fail run_apply
assert_code HEALTH_CHECK_FAILED
echo "$out" | grep -Eq '^status=ROLLED_BACK$' || fail "expected status=ROLLED_BACK, got:\n$out"
[[ "$(sha256sum "$tls/fullchain.pem" | cut -d' ' -f1)" == "$active_before" ]] \
  || fail "active certificate not restored after health failure"

# 5. Happy path installs the candidate and reports certificate metadata.
make_candidate 'req-ok'
candidate_sum="$(sha256sum "$control/candidates/req-ok/fullchain.pem" | cut -d' ' -f1)"
write_request 'req-ok' 'https://gearvia.corp' uploaded "$(sign 'req-ok' 'https://gearvia.corp' uploaded)"
GEARVIA_TEST_HEALTH=ok run_apply
assert_code OK
echo "$out" | grep -Eq '^status=APPLIED$' || fail "expected status=APPLIED, got:\n$out"
[[ "$(sha256sum "$tls/fullchain.pem" | cut -d' ' -f1)" == "$candidate_sum" ]] \
  || fail "candidate certificate was not installed"
result="$control/results/req-ok.env"
[[ -f "$result" ]] || fail "result file not written"
grep -Eq '^certificateSans=.*new\.gearvia\.corp' "$result" || fail "result missing certificate SANs"
grep -Eq '^certificateNotAfter=.+' "$result" || fail "result missing certificate expiry"

echo "Ubuntu host apply tests passed"
