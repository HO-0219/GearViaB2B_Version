#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
grep -q 'location = /mcp' "$root/infra/b2b/nginx.conf.template"
for name in MCP_ENABLED MCP_ALLOWED_CIDRS MCP_TRUSTED_PROXIES MCP_ALLOWED_ORIGINS MCP_MAX_TOOL_CALLS_PER_MINUTE MCP_MAX_CONCURRENT_CALLS; do
  grep -q "$name" "$root/infra/b2b/compose.yml" || { echo "missing Compose setting: $name" >&2; exit 1; }
done
echo "MCP proxy configuration: OK"
