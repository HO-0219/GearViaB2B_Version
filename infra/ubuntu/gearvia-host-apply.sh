#!/usr/bin/env bash
set -euo pipefail

# Root-owned applier for domain/TLS change requests written by the backend into
# /var/lib/gearvia/control/requests. It only ever runs a fixed set of commands
# against fixed paths: it never evaluates request-supplied shell, file paths, or
# Docker arguments.
#
# Usage: gearvia-host-apply.sh <request-file>

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/ubuntu/lib/gearvia-common.sh
. "$script_dir/lib/gearvia-common.sh"
# shellcheck source=infra/ubuntu/lib/gearvia-tls.sh
. "$script_dir/lib/gearvia-tls.sh"

docker_bin() { "${GEARVIA_DOCKER_BIN:-docker}" "$@"; }

control_root="$(gearvia_root /var/lib/gearvia/control)"
requests_dir="$control_root/requests"
results_dir="$control_root/results"
candidates_dir="$control_root/candidates"
tls_root="$(gearvia_root /etc/gearvia/tls)"
runtime_env="$(gearvia_root /etc/gearvia/runtime.env)"
compose_file="$(gearvia_root /opt/b2bgearvia/compose.yml)"
hmac_key_file="$(gearvia_root /etc/gearvia/host-apply.key)"
backup_dir="$(gearvia_root /var/lib/gearvia/recovery/tls.host-apply)"

request_file="${1:-}"
[[ -n "$request_file" && -f "$request_file" ]] || gearvia_die "usage: gearvia-host-apply.sh <request-file>"

request_id=""
result_status="REJECTED"
cert_issuer=""
cert_not_after=""
cert_sans=""

emit() {
  local code="$1"
  printf 'code=%s\n' "$code"
  printf 'status=%s\n' "$result_status"
  if [[ "$request_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]]; then
    mkdir -p "$results_dir"
    {
      printf 'requestId=%s\n' "$request_id"
      printf 'status=%s\n' "$result_status"
      printf 'code=%s\n' "$code"
      printf 'certificateIssuer=%s\n' "$cert_issuer"
      printf 'certificateNotAfter=%s\n' "$cert_not_after"
      printf 'certificateSans=%s\n' "$cert_sans"
    } > "$results_dir/$request_id.env.tmp"
    chmod 0600 "$results_dir/$request_id.env.tmp"
    mv "$results_dir/$request_id.env.tmp" "$results_dir/$request_id.env"
  fi
  [[ "$code" == "OK" ]] && exit 0 || exit 1
}

constant_time_equals() {
  local a b
  a="$(printf '%s' "$1" | sha256sum | cut -d' ' -f1)"
  b="$(printf '%s' "$2" | sha256sum | cut -d' ' -f1)"
  [[ "$a" == "$b" ]]
}

pubkey_hash_from_cert() {
  openssl x509 -in "$1" -pubkey -noout 2>/dev/null \
    | openssl pkey -pubin -outform der 2>/dev/null | sha256sum | cut -d' ' -f1
}
pubkey_hash_from_key() {
  openssl pkey -in "$1" -pubout -outform der 2>/dev/null | sha256sum | cut -d' ' -f1
}

# A single probe of the freshly recreated web container.
host_apply_health_probe() {
  case "${GEARVIA_TEST_HEALTH:-}" in
    ok) return 0 ;;
    fail) return 1 ;;
    slow)
      # Test hook: fail the first probe, then succeed, exercising the retry loop.
      local marker="${GEARVIA_TEST_HEALTH_MARKER:?slow health test needs GEARVIA_TEST_HEALTH_MARKER}"
      local n
      n=$(( $(cat "$marker" 2>/dev/null || echo 0) + 1 ))
      printf '%s' "$n" > "$marker"
      (( n >= 2 ))
      return
      ;;
  esac
  curl --insecure --fail --silent --max-time 5 https://127.0.0.1/healthz >/dev/null 2>&1
}

# `docker compose up -d` returns before nginx has bound :443, so a single probe
# almost always fails on real hardware and forces an unnecessary rollback. Retry
# for up to GEARVIA_HEALTH_ATTEMPTS * GEARVIA_HEALTH_INTERVAL seconds.
host_apply_healthy() {
  local attempts="${GEARVIA_HEALTH_ATTEMPTS:-20}" interval="${GEARVIA_HEALTH_INTERVAL:-2}" i
  for (( i = 0; i < attempts; i++ )); do
    if host_apply_health_probe; then
      return 0
    fi
    (( i + 1 < attempts )) && sleep "$interval"
  done
  return 1
}

recreate_web() {
  docker_bin compose --env-file "$runtime_env" -f "$compose_file" up -d --no-deps --force-recreate web >/dev/null 2>&1
}

request_id="$(gearvia_read_runtime_value "$request_file" requestId || true)"
public_url="$(gearvia_read_runtime_value "$request_file" publicUrl || true)"
certificate_mode="$(gearvia_read_runtime_value "$request_file" certificateMode || true)"
signature="$(gearvia_read_runtime_value "$request_file" signature || true)"

[[ "$request_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || { request_id=""; emit REQUEST_ID_INVALID; }
case "$request_id" in */*|*..*) emit REQUEST_ID_INVALID ;; esac
[[ "$certificate_mode" == "uploaded" || "$certificate_mode" == "self-signed" ]] || emit REQUEST_MODE_INVALID
[[ -r "$hmac_key_file" ]] || gearvia_die "host apply HMAC key is not readable"

hmac_key="$(cat "$hmac_key_file")"
expected_signature="$(printf '%s\n%s\n%s' "$request_id" "$public_url" "$certificate_mode" \
  | openssl dgst -sha256 -hmac "$hmac_key" | sed 's/^.*= //')"
constant_time_equals "$signature" "$expected_signature" || emit REQUEST_SIGNATURE_INVALID

candidate_dir="$candidates_dir/$request_id"
candidate_cert="$candidate_dir/fullchain.pem"
candidate_key="$candidate_dir/privkey.pem"

if [[ "$certificate_mode" == "self-signed" ]]; then
  mkdir -p "$candidate_dir"
  address="$(printf '%s' "${public_url#https://}" | sed 's#[:/].*##')"
  gearvia_issue_server_cert "$candidate_dir" "$address" "$(hostname -f 2>/dev/null || hostname)"
fi

[[ -r "$candidate_cert" && -r "$candidate_key" ]] || emit CANDIDATE_FILES_MISSING
[[ "$(pubkey_hash_from_cert "$candidate_cert")" == "$(pubkey_hash_from_key "$candidate_key")" ]] \
  || emit CERTIFICATE_KEY_MISMATCH

docker_bin compose --env-file "$runtime_env" -f "$compose_file" config --quiet >/dev/null 2>&1 \
  || emit COMPOSE_INVALID

mkdir -p "$backup_dir"
cp -a "$tls_root/fullchain.pem" "$backup_dir/fullchain.pem"
cp -a "$tls_root/privkey.pem" "$backup_dir/privkey.pem"

install -m 0644 "$candidate_cert" "$tls_root/fullchain.pem.new"
install -m 0600 "$candidate_key" "$tls_root/privkey.pem.new"
mv "$tls_root/fullchain.pem.new" "$tls_root/fullchain.pem"
mv "$tls_root/privkey.pem.new" "$tls_root/privkey.pem"

recreate_web
if ! host_apply_healthy; then
  cp -a "$backup_dir/fullchain.pem" "$tls_root/fullchain.pem"
  cp -a "$backup_dir/privkey.pem" "$tls_root/privkey.pem"
  recreate_web
  result_status="ROLLED_BACK"
  emit HEALTH_CHECK_FAILED
fi

cert_issuer="$(openssl x509 -in "$tls_root/fullchain.pem" -noout -issuer 2>/dev/null | sed 's/^issuer= *//' || true)"
cert_not_after="$(openssl x509 -in "$tls_root/fullchain.pem" -noout -enddate 2>/dev/null | sed 's/^notAfter=//' || true)"
# Normalise the OpenSSL date to an ISO-8601 UTC instant the backend can parse.
cert_not_after="$(date -u -d "$cert_not_after" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || printf '%s' "$cert_not_after")"
san_block="$(openssl x509 -in "$tls_root/fullchain.pem" -noout -ext subjectAltName 2>/dev/null || true)"
cert_sans="$(printf '%s\n' "$san_block" | awk '/DNS:|IP Address:/ {gsub(/ /,""); print}' | paste -sd, -)"
result_status="APPLIED"
emit OK
