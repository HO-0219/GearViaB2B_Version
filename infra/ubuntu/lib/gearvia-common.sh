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

gearvia_generate_secret() { openssl rand -base64 "$1" | tr -d '\n'; }

gearvia_read_runtime_value() {
  local file="$1" key="$2" line
  [[ -r "$file" ]] || return 1
  while IFS= read -r line || [[ -n "$line" ]]; do
    case "$line" in
      "$key="*) printf '%s' "${line#*=}"; return 0 ;;
    esac
  done < "$file"
  return 1
}

gearvia_password_is_valid() {
  [[ -n "$1" && "$1" != *[[:cntrl:]]* ]]
}

gearvia_read_db_password() {
  local password_file="${1:-}" password confirmation
  if [[ -n "$password_file" ]]; then
    [[ "$password_file" = /* && -r "$password_file" ]] || gearvia_die "--db-password-file must name a readable absolute file"
    password="$(<"$password_file")"
    gearvia_password_is_valid "$password" || gearvia_die "Database password must not be empty or contain control characters"
    printf '%s' "$password"
    return
  fi

  while true; do
    printf 'MySQL application password: ' >&2
    IFS= read -r -s password
    printf '\nConfirm MySQL application password: ' >&2
    IFS= read -r -s confirmation
    printf '\n' >&2
    if ! gearvia_password_is_valid "$password"; then
      gearvia_log "Database password must not be empty or contain control characters" >&2
    elif [[ "$password" != "$confirmation" ]]; then
      gearvia_log "Database passwords do not match" >&2
    else
      printf '%s' "$password"
      return
    fi
  done
}

gearvia_write_kv() { printf '%s=%s\n' "$1" "$2" >> "$3"; }

gearvia_write_runtime_env() {
  local target="$1" public_url="$2" db_password="$3"
  local active_runtime jwt_secret mfa_secret mysql_root_password domain existing
  active_runtime="$(gearvia_root /etc/gearvia/runtime.env)"

  if existing="$(gearvia_read_runtime_value "$active_runtime" MYSQL_APP_PASSWORD)" && [[ -n "$existing" ]]; then
    db_password="$existing"
  fi
  if existing="$(gearvia_read_runtime_value "$active_runtime" JWT_SECRET)" && [[ -n "$existing" ]]; then
    jwt_secret="$existing"
  else
    jwt_secret="$(gearvia_generate_secret 48)"
  fi
  if existing="$(gearvia_read_runtime_value "$active_runtime" ADMIN_MFA_ENCRYPTION_KEY_BASE64)" && [[ -n "$existing" ]]; then
    mfa_secret="$existing"
  else
    mfa_secret="$(gearvia_generate_secret 32)"
  fi
  if existing="$(gearvia_read_runtime_value "$active_runtime" MYSQL_ROOT_PASSWORD)" && [[ -n "$existing" ]]; then
    mysql_root_password="$existing"
  else
    mysql_root_password="$(gearvia_generate_secret 32)"
  fi

  domain="${public_url#https://}"
  domain="${domain%%/*}"
  local keys=(
    APP_ENVIRONMENT COMPOSE_PROFILES DOMAIN_NAME JWT_SECRET AUTH_SECURE_COOKIE
    SPRING_JPA_HIBERNATE_DDL_AUTO DEMO_ENABLED ADMIN_ENABLED ADMIN_MFA_ENCRYPTION_KEY_BASE64
    ADMIN_ALLOWED_IPS ADMIN_TRUSTED_PROXIES FRONTEND_URL ADMIN_FRONTEND_URL INSTANCE_ID
    DB_POOL_MAX_SIZE DB_POOL_MIN_IDLE DB_POOL_CONNECTION_TIMEOUT_MS HTTP_MAX_THREADS HTTP_ACCEPT_COUNT
    QUERY_MAX_TASK_RESULTS DOCUMENT_INDEX_CORE_SIZE DOCUMENT_INDEX_MAX_SIZE DOCUMENT_INDEX_QUEUE_CAPACITY
    DOCUMENT_INDEX_KEEP_ALIVE_SECONDS NOTIFICATION_CORE_SIZE NOTIFICATION_MAX_SIZE
    NOTIFICATION_QUEUE_CAPACITY NOTIFICATION_KEEP_ALIVE_SECONDS RESOURCE_WARNING_PERCENT
    RESOURCE_CRITICAL_PERCENT STORAGE_PROVIDER STORAGE_NAS_ROOT AI_SUPPORTED_MODELS
    AI_ASSISTANT_EMBEDDING_MODEL MCP_ENABLED MCP_ALLOWED_CIDRS MCP_TRUSTED_PROXIES
    MCP_ALLOWED_ORIGINS MCP_MAX_TOOL_CALLS_PER_MINUTE MCP_MAX_CONCURRENT_CALLS
    MYSQL_APP_PASSWORD MYSQL_ROOT_PASSWORD MYSQL_IMAGE INIT_DATA_IMAGE BACKEND_IMAGE WEB_IMAGE
    TLS_CERT_FILE TLS_KEY_FILE
  )
  local values=(
    b2b-production bundled-db "$domain" "$jwt_secret" true validate false true "$mfa_secret"
    192.168.0.0/16 '10.0.0.0/8,172.16.0.0/12,192.168.0.0/16' "$public_url" "$public_url" ''
    20 5 30000 100 100 1000 1 2 100 60 2 4 500 60 75 90 local
    /opt/b2bgearvia/data/nas 'gpt-5.6-sol,gpt-5.6-luna' text-embedding-3-small false
    '10.0.0.0/8,172.16.0.0/12,192.168.0.0/16' 172.16.0.0/12 "$public_url" 120 2
    "$db_password" "$mysql_root_password" 'mysql:8.4@sha256:<release-digest>'
    'busybox:1.37@sha256:<release-digest>' 'b2bgearvia-backend@sha256:<release-digest>'
    'b2bgearvia-web@sha256:<release-digest>' ./tls/fullchain.pem ./tls/privkey.pem
  )

  (
    umask 077
    : > "$target"
    chmod 0600 "$target"
    local index
    for index in "${!keys[@]}"; do
      gearvia_write_kv "${keys[$index]}" "${values[$index]}" "$target"
    done
  )
}

gearvia_validate_ubuntu() {
  local release_file
  release_file="$(gearvia_root /etc/os-release)"
  [[ -r "$release_file" ]] || gearvia_die "Ubuntu release information is unavailable"
  local ID="" VERSION_ID=""
  # shellcheck disable=SC1090
  . "$release_file"
  [[ "$ID" == "ubuntu" ]] || gearvia_die "Only Ubuntu Server is supported"
  [[ "$VERSION_ID" == "24.04" ]] || gearvia_die "Only Ubuntu Server 24.04 LTS is supported"
  local architecture="${GEARVIA_TEST_ARCH:-$(uname -m)}"
  [[ "$architecture" == "x86_64" ]] || gearvia_die "Only x86_64 architecture is supported"
}

gearvia_safe_purge_path() {
  local logical="$1" actual="$2"
  case "$logical" in /opt/b2bgearvia/data|/var/lib/gearvia) ;; *) gearvia_die "Refusing unsafe purge target: $logical" ;; esac
  [[ "$actual" == "$(gearvia_root "$logical")" && "$actual" != "/" ]] || gearvia_die "Purge path escaped the GearVia root"
}
