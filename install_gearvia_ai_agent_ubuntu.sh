#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/ubuntu/lib/gearvia-common.sh
. "$script_dir/infra/ubuntu/lib/gearvia-common.sh"

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

provided_db_password=""
if [[ -n "$db_password_file" ]]; then
  provided_db_password="$(gearvia_read_db_password "$db_password_file")"
fi
if existing_db_password="$(gearvia_read_runtime_value "$active_runtime" MYSQL_APP_PASSWORD)" && [[ -n "$existing_db_password" ]]; then
  db_password="$existing_db_password"
elif [[ -n "$provided_db_password" ]]; then
  db_password="$provided_db_password"
else
  db_password="$(gearvia_read_db_password)"
fi

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

runtime_candidate="$(mktemp "${TMPDIR:-/tmp}/gearvia-runtime.XXXXXX")"
trap 'rm -f -- "${runtime_candidate:-}"' EXIT
gearvia_write_runtime_env "$runtime_candidate" "https://127.0.0.1" "$db_password"
if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then
  docker compose --env-file "$runtime_candidate" -f "$script_dir/infra/b2b/compose.yml" config --quiet
fi

if [[ -n "${GEARVIA_TEST_ROOT:-}" ]]; then
  mkdir -p "$install_root/data/uploads" "$install_root/data/nas" "$install_root/config" "$config_root" "$state_root/recovery"
else
install -d -m 0755 "$install_root" "$install_root/data/uploads" "$install_root/data/nas" "$install_root/config" "$install_root/tls"
  install -d -m 0700 "$config_root" "$state_root" "$state_root/recovery"
fi
install -m 0644 "$script_dir/infra/b2b/compose.yml" "$install_root/compose.yml"
if [[ -f "$config_root/runtime.env" ]]; then
  install -m 0600 "$config_root/runtime.env" "$state_root/recovery/runtime.env.previous"
fi
install -m 0600 "$runtime_candidate" "$config_root/runtime.env"
install -D -m 0644 "$script_dir/infra/b2b/systemd/b2bgearvia.service" "$unit_path"
printf 'INSTALL_VERSION=1\nINSTALLED_AT=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$state_root/install-state.env"
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
fi
install -m 0600 "$config_root/runtime.env" "$state_root/recovery/runtime.env.last-known-good"
gearvia_log "Installation completed. Runtime data and database volumes are preserved across reruns."
