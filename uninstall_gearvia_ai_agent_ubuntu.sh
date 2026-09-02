#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/ubuntu/lib/gearvia-common.sh
. "$script_dir/infra/ubuntu/lib/gearvia-common.sh"

purge=false
confirmation=""
usage() { echo "Usage: sudo $0 [--purge-data --confirm-purge GEARVIA]"; }
while [[ $# -gt 0 ]]; do
  case "$1" in
    --purge-data|--purge) purge=true; shift ;;
    --confirm-purge) [[ $# -ge 2 ]] || gearvia_die "--confirm-purge requires a value"; confirmation="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; gearvia_die "Unknown option: $1" ;;
  esac
done
[[ "$purge" == false || "$confirmation" == "GEARVIA" ]] || gearvia_die "Data purge requires --confirm-purge GEARVIA"
gearvia_require_root

install_root="$(gearvia_root /opt/b2bgearvia)"
config_root="$(gearvia_root /etc/gearvia)"
state_root="$(gearvia_root /var/lib/gearvia)"
unit_path="$(gearvia_root /etc/systemd/system/b2bgearvia.service)"
host_apply_unit="$(gearvia_root /etc/systemd/system/gearvia-host-apply.service)"
host_apply_path_unit="$(gearvia_root /etc/systemd/system/gearvia-host-apply.path)"

if [[ -r "$config_root/runtime.env" ]]; then
  mkdir -p "$state_root/recovery"
  recovery_candidate="$(mktemp "${TMPDIR:-/tmp}/gearvia-database.XXXXXX")"
  trap 'rm -f -- "${recovery_candidate:-}"' EXIT
  : > "$recovery_candidate"
  for key in MYSQL_APP_PASSWORD MYSQL_ROOT_PASSWORD; do
    if value="$(gearvia_read_runtime_value "$config_root/runtime.env" "$key")" && [[ -n "$value" ]]; then
      gearvia_write_kv "$key" "$value" "$recovery_candidate"
    fi
  done
  install -m 0600 "$recovery_candidate" "$state_root/recovery/database.env"
fi

if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then
  systemctl disable --now gearvia-host-apply.path >/dev/null 2>&1 || true
  systemctl disable --now b2bgearvia.service >/dev/null 2>&1 || true
  if command -v docker >/dev/null 2>&1 && [[ -f "$install_root/compose.yml" && -f "$config_root/runtime.env" ]]; then
    docker compose --env-file "$config_root/runtime.env" -f "$install_root/compose.yml" down || true
  fi
fi

rm -f -- "$install_root/compose.yml" "$unit_path" "$host_apply_unit" "$host_apply_path_unit" \
  "$config_root/runtime.env" "$config_root/host-apply.key"
rm -rf -- "$config_root/tls" "$install_root/host-apply" "$state_root/control" \
  "$state_root/recovery/tls.host-apply"
rm -f -- "$state_root/recovery/runtime.env.previous" "$state_root/recovery/runtime.env.last-known-good"
rm -rf -- "$state_root/recovery/tls.previous" "$state_root/recovery/tls.last-known-good"
rmdir "$config_root" 2>/dev/null || true
if [[ "$purge" == true ]]; then
  data_path="$install_root/data"
  gearvia_safe_purge_path /opt/b2bgearvia/data "$data_path"
  rm -rf -- "$data_path"
  gearvia_safe_purge_path /var/lib/gearvia "$state_root"
  rm -rf -- "$state_root"
  gearvia_log "Application and explicitly confirmed GearVia data were removed"
else
  gearvia_log "Application removed; database volumes, NAS files, local data, and recovery state were preserved"
fi
if [[ "${GEARVIA_SKIP_RUNTIME:-0}" != "1" ]]; then systemctl daemon-reload; fi
