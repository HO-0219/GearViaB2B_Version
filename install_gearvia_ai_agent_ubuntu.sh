#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/ubuntu/lib/gearvia-common.sh
. "$script_dir/infra/ubuntu/lib/gearvia-common.sh"
# shellcheck source=infra/ubuntu/lib/gearvia-tls.sh
. "$script_dir/infra/ubuntu/lib/gearvia-tls.sh"
# shellcheck source=infra/ubuntu/lib/gearvia-images.sh
. "$script_dir/infra/ubuntu/lib/gearvia-images.sh"

dry_run=false
db_password_file=""
usage() { echo "Usage: sudo $0 [--dry-run] [--db-password-file /absolute/file]"; }
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) dry_run=true; shift ;;
    --db-password-file) [[ $# -ge 2 ]] || gearvia_die "--db-password-file requires a file"; db_password_file="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; gearvia_die "Unknown option: $1" ;;
  esac
done

gearvia_validate_ubuntu
gearvia_require_root
[[ -f "$script_dir/infra/b2b/compose.yml" ]] || gearvia_die "Run from a complete GearVia release bundle"

install_root="$(gearvia_root /opt/b2bgearvia)"
config_root="$(gearvia_root /etc/gearvia)"
state_root="$(gearvia_root /var/lib/gearvia)"
unit_path="$(gearvia_root /etc/systemd/system/b2bgearvia.service)"
active_runtime="$config_root/runtime.env"
tls_root="$config_root/tls"

provided_db_password=""
if [[ -n "$db_password_file" ]]; then
  provided_db_password="$(gearvia_read_db_password "$db_password_file")"
fi
recovery_database="$state_root/recovery/database.env"
db_password_source="$active_runtime"
if [[ ! -r "$db_password_source" && -r "$recovery_database" ]]; then db_password_source="$recovery_database"; fi
if existing_db_password="$(gearvia_read_runtime_value "$db_password_source" MYSQL_APP_PASSWORD)" && [[ -n "$existing_db_password" ]]; then
  db_password="$existing_db_password"
elif [[ -n "$provided_db_password" ]]; then
  db_password="$provided_db_password"
else
  db_password="$(gearvia_read_db_password)"
fi
public_address="$(gearvia_detect_primary_address)"
host_name="${GEARVIA_TEST_HOSTNAME:-$(hostname -f 2>/dev/null || hostname)}"
public_url="https://$public_address"

gearvia_log "Validated Ubuntu host, release bundle, and runtime configuration"
if [[ "$dry_run" == true ]]; then
  gearvia_log "DRY RUN: would install runtime files, validate Compose, and start b2bgearvia.service"
  exit 0
fi

command -v openssl >/dev/null 2>&1 || gearvia_die "OpenSSL is required to generate runtime secrets"
if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then
  command -v docker >/dev/null 2>&1 || gearvia_die "Docker Engine with Compose v2 is required"
  docker compose version >/dev/null 2>&1 || gearvia_die "Docker Compose v2 is required"
  command -v curl >/dev/null 2>&1 || gearvia_die "curl is required for readiness verification"
fi

release_tag="${GEARVIA_RELEASE_TAG:-$(git -C "$script_dir" rev-parse --short=12 HEAD 2>/dev/null || printf 'bundle')}"
GEARVIA_BACKEND_IMAGE="b2bgearvia-backend:$release_tag"
GEARVIA_WEB_IMAGE="b2bgearvia-web:$release_tag"
GEARVIA_MYSQL_IMAGE="mysql:8.4"
GEARVIA_INIT_DATA_IMAGE="busybox:1.37"
export GEARVIA_REPO_ROOT="$script_dir" GEARVIA_BACKEND_IMAGE GEARVIA_WEB_IMAGE GEARVIA_MYSQL_IMAGE GEARVIA_INIT_DATA_IMAGE
if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then
  gearvia_prepare_image backend "$GEARVIA_BACKEND_IMAGE" backend/Dockerfile >/dev/null
  gearvia_prepare_image web "$GEARVIA_WEB_IMAGE" frontend/Dockerfile >/dev/null
  gearvia_prepare_image mysql "$GEARVIA_MYSQL_IMAGE" '' >/dev/null
  gearvia_prepare_image init-data "$GEARVIA_INIT_DATA_IMAGE" '' >/dev/null
fi

runtime_candidate="$(mktemp "${TMPDIR:-/tmp}/gearvia-runtime.XXXXXX")"
tls_candidate="$(mktemp -d "${TMPDIR:-/tmp}/gearvia-tls.XXXXXX")"
trap 'rm -f -- "${runtime_candidate:-}"; rm -rf -- "${tls_candidate:-}"' EXIT
if [[ -d "$tls_root" ]]; then cp -a "$tls_root/." "$tls_candidate/"; fi
gearvia_issue_server_cert "$tls_candidate" "$public_address" "$host_name"
gearvia_write_runtime_env "$runtime_candidate" "$public_url" "$db_password"
if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then
  docker compose --env-file "$runtime_candidate" -f "$script_dir/infra/b2b/compose.yml" config --quiet
fi

control_root="$state_root/control"
host_apply_root="$install_root/host-apply"
if [[ -n "${GEARVIA_TEST_ROOT:-}" ]]; then
  mkdir -p "$install_root/data/uploads" "$install_root/data/nas" "$install_root/config" "$config_root" "$tls_root" "$state_root/recovery" \
    "$control_root/requests" "$control_root/results" "$control_root/candidates" "$host_apply_root/lib"
else
  install -d -m 0755 "$install_root" "$install_root/data/uploads" "$install_root/data/nas" "$install_root/config"
  install -d -m 0700 "$config_root" "$tls_root" "$state_root" "$state_root/recovery" \
    "$control_root" "$control_root/requests" "$control_root/results" "$control_root/candidates"
  install -d -m 0755 "$host_apply_root" "$host_apply_root/lib"
fi
install -m 0644 "$script_dir/infra/b2b/compose.yml" "$install_root/compose.yml"
install -m 0700 "$script_dir/infra/ubuntu/gearvia-host-apply.sh" "$host_apply_root/gearvia-host-apply.sh"
install -m 0644 "$script_dir/infra/ubuntu/lib/gearvia-common.sh" "$host_apply_root/lib/gearvia-common.sh"
install -m 0644 "$script_dir/infra/ubuntu/lib/gearvia-tls.sh" "$host_apply_root/lib/gearvia-tls.sh"
if [[ -f "$config_root/runtime.env" ]]; then
  install -m 0600 "$config_root/runtime.env" "$state_root/recovery/runtime.env.previous"
fi
install -m 0600 "$runtime_candidate" "$config_root/runtime.env"
install -m 0600 "$tls_candidate/ca.key" "$tls_root/ca.key"
install -m 0644 "$tls_candidate/ca.crt" "$tls_root/ca.crt"
install -m 0600 "$tls_candidate/privkey.pem" "$tls_root/privkey.pem"
install -m 0644 "$tls_candidate/fullchain.pem" "$tls_root/fullchain.pem"
host_apply_key="$(gearvia_read_runtime_value "$config_root/runtime.env" HOST_APPLY_REQUEST_HMAC_KEY || true)"
[[ -n "$host_apply_key" ]] || gearvia_die "runtime configuration is missing HOST_APPLY_REQUEST_HMAC_KEY"
( umask 077; printf '%s' "$host_apply_key" > "$config_root/host-apply.key" )
chmod 0600 "$config_root/host-apply.key"
install -D -m 0644 "$script_dir/infra/b2b/systemd/b2bgearvia.service" "$unit_path"
install -D -m 0644 "$script_dir/infra/ubuntu/systemd/gearvia-host-apply.service" "$(gearvia_root /etc/systemd/system/gearvia-host-apply.service)"
install -D -m 0644 "$script_dir/infra/ubuntu/systemd/gearvia-host-apply.path" "$(gearvia_root /etc/systemd/system/gearvia-host-apply.path)"
printf 'INSTALL_VERSION=1\nINSTALLED_AT=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$state_root/install-state.env"
if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then gearvia_record_image_state "$state_root/install-state.env"; fi
chmod 0600 "$state_root/install-state.env"

if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then
  docker compose --env-file "$config_root/runtime.env" -f "$install_root/compose.yml" config --quiet
  systemctl daemon-reload
  if ! systemctl enable --now b2bgearvia.service; then
    if [[ -f "$state_root/recovery/runtime.env.previous" ]]; then
      install -m 0600 "$state_root/recovery/runtime.env.previous" "$config_root/runtime.env"
      systemctl restart b2bgearvia.service >/dev/null 2>&1 || true
    fi
    gearvia_die "Service startup failed; previous runtime configuration was restored"
  fi
  ready=false
  for _ in {1..60}; do
    if curl --insecure --fail --silent --max-time 5 https://127.0.0.1/api/v1/health/ready >/dev/null 2>&1; then ready=true; break; fi
    sleep 2
  done
  if [[ "$ready" != true ]]; then
    [[ ! -f "$state_root/recovery/runtime.env.previous" ]] || install -m 0600 "$state_root/recovery/runtime.env.previous" "$config_root/runtime.env"
    systemctl restart b2bgearvia.service >/dev/null 2>&1 || true
    gearvia_die "Readiness failed; previous runtime configuration was restored"
  fi
  systemctl enable --now gearvia-host-apply.path >/dev/null 2>&1 || true
fi
install -m 0600 "$config_root/runtime.env" "$state_root/recovery/runtime.env.last-known-good"
gearvia_log "Installation completed. Runtime data and database volumes are preserved across reruns."
