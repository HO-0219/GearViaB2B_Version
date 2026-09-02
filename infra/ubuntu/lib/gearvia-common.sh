#!/usr/bin/env bash

gearvia_die() { printf '[GearVia] ERROR: %s\n' "$*" >&2; exit 1; }
gearvia_log() { printf '[GearVia] %s\n' "$*"; }

gearvia_root() {
  local root="${GEARVIA_TEST_ROOT:-}"
  if [[ -n "$root" ]]; then
    [[ "$root" = /* && "$root" != "/" ]] || gearvia_die "GEARVIA_TEST_ROOT must be an absolute non-root test path"
    printf '%s%s' "${root%/}" "$1"
  else
    printf '%s' "$1"
  fi
}

gearvia_require_root() {
  [[ -n "${GEARVIA_TEST_ROOT:-}" || "${EUID:-$(id -u)}" -eq 0 ]] || gearvia_die "Run with sudo"
}

gearvia_validate_ubuntu() {
  local release_file
  release_file="$(gearvia_root /etc/os-release)"
  [[ -r "$release_file" ]] || gearvia_die "Ubuntu release information is unavailable"
  local ID="" VERSION_ID=""
  # shellcheck disable=SC1090
  . "$release_file"
  [[ "$ID" == "ubuntu" ]] || gearvia_die "Only Ubuntu Server is supported"
  case "$VERSION_ID" in 22.04|24.04) ;; *) gearvia_die "Supported Ubuntu releases are 22.04 and 24.04" ;; esac
  case "$(uname -m)" in x86_64|aarch64|arm64) ;; *) gearvia_die "Unsupported architecture: $(uname -m)" ;; esac
}

gearvia_safe_purge_path() {
  local logical="$1" actual="$2"
  case "$logical" in /opt/b2bgearvia/data|/var/lib/gearvia) ;; *) gearvia_die "Refusing unsafe purge target: $logical" ;; esac
  [[ "$actual" == "$(gearvia_root "$logical")" && "$actual" != "/" ]] || gearvia_die "Purge path escaped the GearVia root"
}
