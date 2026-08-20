#!/usr/bin/env bash
set -euo pipefail

# Configure optional OpenAI integration without ever printing the key.
CONFIG_FILE="${B2B_AI_ENV_FILE:-/opt/b2bgearvia/config/ai.env}"
COMPOSE_CMD="${B2B_COMPOSE_CMD:-docker compose}"
COMPOSE_FILE="${B2B_COMPOSE_FILE:-/opt/b2bgearvia/docker-compose.yml}"
ACTION="${1:-status}"

die() { echo "configure-ai: $*" >&2; exit 2; }
[[ "$CONFIG_FILE" = /* ]] || die "B2B_AI_ENV_FILE must be an absolute path"
umask 077
mkdir -p "$(dirname "$CONFIG_FILE")"

status() {
  if [[ ! -f "$CONFIG_FILE" ]]; then echo "AI configuration: disabled (no key file)"; return; fi
  local key="" enabled="false"
  key="$(sed -n 's/^OPENAI_API_KEY=//p' "$CONFIG_FILE" | head -n1)"
  enabled="$(sed -n 's/^AI_REPORT_ENABLED=//p' "$CONFIG_FILE" | head -n1)"
  if [[ -n "$key" ]]; then echo "AI configuration: ${enabled:-false} (key configured: ****${key: -4})"; else echo "AI configuration: disabled (no key)"; fi
}
write_config() {
  local key="$1" tmp
  tmp="${CONFIG_FILE}.tmp.$$"
  { printf 'OPENAI_API_KEY=%s\n' "$key"; printf 'AI_REPORT_ENABLED=%s\n' "${AI_REPORT_ENABLED:-true}"; printf 'AI_ASSISTANT_ENABLED=%s\n' "${AI_ASSISTANT_ENABLED:-true}"; } > "$tmp"
  chmod 600 "$tmp"
  mv -f "$tmp" "$CONFIG_FILE"
}
restart() {
  [[ "${B2B_AI_NO_RESTART:-false}" = true ]] && return
  # shellcheck disable=SC2086
  ${COMPOSE_CMD} -f "$COMPOSE_FILE" up -d --force-recreate backend >/dev/null
}
case "$ACTION" in
  set|replace)
    read -r -s -p "OpenAI API key (hidden): " key; echo
    [[ -n "$key" ]] || die "key must not be empty"
    write_config "$key"; unset key; restart; status ;;
  delete)
    if [[ -f "$CONFIG_FILE" ]]; then rm -f -- "$CONFIG_FILE"; fi
    restart; status ;;
  status) status ;;
  *) die "usage: configure-ai {set|replace|delete|status}" ;;
esac
