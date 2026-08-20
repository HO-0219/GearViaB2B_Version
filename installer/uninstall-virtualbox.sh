#!/usr/bin/env bash
set -euo pipefail

INSTALL_ROOT="${B2B_INSTALL_ROOT:-/opt/b2bgearvia}"
ACTION="${1:-}"

log() { printf '[B2BGearVia] %s\n' "$*"; }
die() { printf '[B2BGearVia] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: sudo ./installer/uninstall-virtualbox.sh [--yes|--dry-run]

  --dry-run  Show exactly what will be deleted without changing the server
  --yes      Skip the DELETE confirmation prompt

The B2BGearVia containers, images, database, uploads, network and
/opt/b2bgearvia runtime directory are deleted. Docker Engine and the Git clone remain.
EOF
}

case "$ACTION" in
  --dry-run|--yes|"") ;;
  --help|-h) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac

[[ "$INSTALL_ROOT" = /* ]] || die "B2B_INSTALL_ROOT must be an absolute path."
[[ "$INSTALL_ROOT" == */b2bgearvia ]] || die "Refusing unsafe install root: $INSTALL_ROOT"

log "B2BGearVia VirtualBox removal plan"
log "Install root: $INSTALL_ROOT"
log "Delete: B2BGearVia containers and network"
log "Delete volume: b2bgearvia-mysql-data"
log "Delete volume: b2bgearvia-uploads"
log "Delete: B2BGearVia application images"
log "Git clone: keep"
log "Docker Engine: keep"

if [[ "$ACTION" == "--dry-run" ]]; then exit 0; fi
[[ "${EUID:-$(id -u)}" -eq 0 ]] || die "Run this uninstaller with sudo."

if [[ "$ACTION" != "--yes" ]]; then
  printf '\nAll B2BGearVia database and uploaded files will be permanently deleted.\n'
  read -r -p 'Type DELETE to continue: ' confirmation
  [[ "$confirmation" == "DELETE" ]] || die "Removal cancelled."
fi

if command -v docker >/dev/null 2>&1; then
  if [[ -f "$INSTALL_ROOT/runtime.env" && -f "$INSTALL_ROOT/docker-compose.yml" ]]; then
    compose=(docker compose --env-file "$INSTALL_ROOT/runtime.env" -f "$INSTALL_ROOT/docker-compose.yml")
    [[ -f "$INSTALL_ROOT/compose.virtualbox.yml" ]] && compose+=(-f "$INSTALL_ROOT/compose.virtualbox.yml")
    "${compose[@]}" down --volumes --remove-orphans || true
  fi

  docker rm -f \
    b2bgearvia-web-1 b2bgearvia-backend-1 b2bgearvia-mysql-1 b2bgearvia-init-data-1 \
    >/dev/null 2>&1 || true
  docker volume rm b2bgearvia-mysql-data b2bgearvia-uploads >/dev/null 2>&1 || true
  docker network rm b2bgearvia-internal >/dev/null 2>&1 || true
  docker image rm b2bgearvia-backend:virtualbox b2bgearvia-web:virtualbox >/dev/null 2>&1 || true
fi

if [[ -e "$INSTALL_ROOT" ]]; then rm -rf -- "$INSTALL_ROOT"; fi

log "B2BGearVia application data and runtime files were deleted."
log "Docker Engine and the Git clone were kept."
