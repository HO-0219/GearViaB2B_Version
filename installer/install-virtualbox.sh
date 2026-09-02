#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INSTALL_ROOT="${B2B_INSTALL_ROOT:-/opt/b2bgearvia}"
DRY_RUN="${B2B_DRY_RUN:-false}"
VM_IP="${B2B_VM_IP:-}"
COMPOSE=(docker compose --env-file "$INSTALL_ROOT/runtime.env" -f "$INSTALL_ROOT/docker-compose.yml" -f "$INSTALL_ROOT/compose.virtualbox.yml")

log() { printf '[B2BGearVia] %s\n' "$*"; }
die() { printf '[B2BGearVia] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: sudo ./installer/install-virtualbox.sh

Environment overrides:
  B2B_VM_IP=192.168.56.101   VirtualBox Host-Only address
  B2B_INSTALL_ROOT=/opt/...  Runtime installation directory
  B2B_DRY_RUN=true           Print the installation plan without changes
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then usage; exit 0; fi
[[ $# -eq 0 ]] || { usage >&2; exit 2; }
[[ "$INSTALL_ROOT" = /* ]] || die "B2B_INSTALL_ROOT must be an absolute path"

detect_vm_ip() {
  local default_interface="" interface="" address="" fallback=""
  default_interface="$(ip -4 route show default 2>/dev/null | awk 'NR == 1 { print $5 }')"
  while read -r interface address; do
    [[ -n "$address" ]] || continue
    [[ "$interface" =~ ^(docker|br-|veth|lo) ]] && continue
    if [[ "$interface" != "$default_interface" ]]; then printf '%s' "$address"; return; fi
    [[ -n "$fallback" ]] || fallback="$address"
  done < <(ip -4 -o addr show scope global | awk '{ split($4, value, "/"); print $2, value[1] }')
  printf '%s' "$fallback"
}

valid_ipv4() {
  local ip="$1" part
  [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1
  IFS=. read -r -a parts <<< "$ip"
  for part in "${parts[@]}"; do (( 10#$part >= 0 && 10#$part <= 255 )) || return 1; done
}

if [[ -z "$VM_IP" ]]; then
  if command -v ip >/dev/null 2>&1; then VM_IP="$(detect_vm_ip)"; fi
fi
[[ -n "$VM_IP" ]] || die "VirtualBox IP was not detected. Re-run with B2B_VM_IP=<Host-Only-IP>."
valid_ipv4 "$VM_IP" || die "Invalid B2B_VM_IP: $VM_IP"

log "B2BGearVia VirtualBox automatic installation"
log "Source repository: $REPOSITORY_ROOT"
log "Install root: $INSTALL_ROOT"
log "VirtualBox address: $VM_IP"

if [[ "$DRY_RUN" == "true" ]]; then
  log "Docker Engine: install or verify"
  log "Generate runtime secrets"
  log "Create TLS certificate"
  log "Build application images"
  log "Start B2BGearVia"
  log "Wait for application readiness"
  log "AI integration: skipped (configure later if required)"
  exit 0
fi

[[ "${EUID:-$(id -u)}" -eq 0 ]] || die "Run this installer with sudo."
[[ -f "$REPOSITORY_ROOT/backend/Dockerfile" ]] || die "Run the script from a complete B2BGearVia clone."
[[ -f "$REPOSITORY_ROOT/frontend/Dockerfile" ]] || die "Frontend Dockerfile is missing."
[[ -f "$REPOSITORY_ROOT/infra/b2b/compose.yml" ]] || die "B2B Compose file is missing."

install_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "Docker Engine: already installed"
    systemctl enable --now docker >/dev/null 2>&1 || true
    return
  fi

  [[ -r /etc/os-release ]] || die "Ubuntu release information is unavailable."
  # shellcheck disable=SC1091
  . /etc/os-release
  [[ "${ID:-}" == "ubuntu" ]] || die "Automatic Docker installation supports Ubuntu Server only."

  log "Docker Engine: installing from the official Docker repository"
  apt-get update
  apt-get install -y ca-certificates curl
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  printf 'Types: deb\nURIs: https://download.docker.com/linux/ubuntu\nSuites: %s\nComponents: stable\nArchitectures: %s\nSigned-By: /etc/apt/keyrings/docker.asc\n' \
    "${UBUNTU_CODENAME:-$VERSION_CODENAME}" "$(dpkg --print-architecture)" > /etc/apt/sources.list.d/docker.sources
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
}

install_docker
command -v openssl >/dev/null 2>&1 || { apt-get update; apt-get install -y openssl; }
command -v curl >/dev/null 2>&1 || { apt-get update; apt-get install -y curl; }

log "Preparing runtime files in $INSTALL_ROOT"
install -d -m 0755 "$INSTALL_ROOT" "$INSTALL_ROOT/tls" "$INSTALL_ROOT/config"
install -d -m 0700 -o 10001 -g 10001 "$INSTALL_ROOT/bootstrap"
install -m 0644 "$REPOSITORY_ROOT/infra/b2b/compose.yml" "$INSTALL_ROOT/docker-compose.yml"
install -m 0644 "$REPOSITORY_ROOT/infra/b2b/compose.virtualbox.yml" "$INSTALL_ROOT/compose.virtualbox.yml"

if [[ ! -f "$INSTALL_ROOT/runtime.env" ]]; then
  log "Generate runtime secrets"
  umask 077
  admin_network="${VM_IP%.*}.0/24"
  jwt_secret="$(openssl rand -base64 48 | tr -d '\n')"
  mfa_secret="$(openssl rand -base64 32 | tr -d '\n')"
  mysql_password="$(openssl rand -hex 24)"
  cat > "$INSTALL_ROOT/runtime.env" <<EOF
APP_ENVIRONMENT=b2b-production
COMPOSE_PROFILES=bundled-db
DOMAIN_NAME=$VM_IP
JWT_SECRET=$jwt_secret
AUTH_SECURE_COOKIE=true
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
DEMO_ENABLED=false
ADMIN_ENABLED=true
ADMIN_MFA_ENCRYPTION_KEY_BASE64=$mfa_secret
ADMIN_ALLOWED_IPS=$admin_network
ADMIN_TRUSTED_PROXIES=172.16.0.0/12
FRONTEND_URL=https://$VM_IP
ADMIN_FRONTEND_URL=https://$VM_IP
MYSQL_APP_PASSWORD=$mysql_password
MYSQL_ROOT_PASSWORD=gearvia
MYSQL_IMAGE=mysql:8.4
INIT_DATA_IMAGE=busybox:1.37
BACKEND_IMAGE=b2bgearvia-backend:virtualbox
WEB_IMAGE=b2bgearvia-web:virtualbox
TLS_CERT_FILE=./tls/fullchain.pem
TLS_KEY_FILE=./tls/privkey.pem
EOF
  chmod 600 "$INSTALL_ROOT/runtime.env"
else
  log "Runtime configuration: keeping existing file"
fi

if [[ ! -f "$INSTALL_ROOT/tls/fullchain.pem" || ! -f "$INSTALL_ROOT/tls/privkey.pem" ]]; then
  log "Create TLS certificate for $VM_IP"
  openssl req -x509 -nodes -newkey rsa:3072 -days 365 \
    -keyout "$INSTALL_ROOT/tls/privkey.pem" \
    -out "$INSTALL_ROOT/tls/fullchain.pem" \
    -subj "/CN=$VM_IP" -addext "subjectAltName=IP:$VM_IP"
  chmod 600 "$INSTALL_ROOT/tls/privkey.pem"
  chmod 644 "$INSTALL_ROOT/tls/fullchain.pem"
else
  log "TLS certificate: keeping existing files"
fi

INITIAL_CREDENTIALS="$INSTALL_ROOT/config/initial-admin.txt"
BOOTSTRAP_ADMIN="$INSTALL_ROOT/bootstrap/admin.env"
if [[ ! -f "$INITIAL_CREDENTIALS" && ! -f "$BOOTSTRAP_ADMIN" ]]; then
  log "Create the first administrator"
  cat > "$BOOTSTRAP_ADMIN" <<EOF
username=admin
email=admin@b2bgearvia.local
name=B2BGearVia 관리자
password=admin
EOF
  chown 10001:10001 "$BOOTSTRAP_ADMIN"
  chmod 600 "$BOOTSTRAP_ADMIN"
  cat > "$INITIAL_CREDENTIALS" <<EOF
B2BGearVia initial administrator
URL=https://$VM_IP
username=admin
password=admin

Change this password immediately after the first login, then delete this file.
EOF
  chmod 600 "$INITIAL_CREDENTIALS"
else
  log "Initial administrator: keeping existing state"
fi

log "Build application images"
docker build -f "$REPOSITORY_ROOT/backend/Dockerfile" -t b2bgearvia-backend:virtualbox "$REPOSITORY_ROOT"
docker build -f "$REPOSITORY_ROOT/frontend/Dockerfile" \
  --build-arg "VITE_PUBLIC_SITE_URL=https://$VM_IP" \
  -t b2bgearvia-web:virtualbox "$REPOSITORY_ROOT"

log "Start B2BGearVia"
cd "$INSTALL_ROOT"
if ! "${COMPOSE[@]}" up -d; then
  log "Container startup failed. Collecting dependency diagnostics."
  "${COMPOSE[@]}" ps --all >&2 || true
  "${COMPOSE[@]}" logs --tail=100 init-data mysql backend web >&2 || true
  die "A required container failed. Review the service logs above."
fi

log "Wait for application readiness"
ready=false
for _ in {1..60}; do
  if curl --insecure --fail --silent --show-error https://127.0.0.1/healthz >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 3
done
if [[ "$ready" != "true" ]]; then
  "${COMPOSE[@]}" ps >&2 || true
  "${COMPOSE[@]}" logs --tail=100 backend web >&2 || true
  die "Services did not become ready. Review the logs above."
fi
"${COMPOSE[@]}" ps

log "AI integration: skipped (configure later with installer/commands/configure-ai.sh set)"
log "Installation completed"
log "Open: https://$VM_IP"
log "Initial credentials: $INITIAL_CREDENTIALS"
log "The browser warning is expected for the generated test certificate."
