# GearVia Personal MCP Access

## Deployment Boundary

Expose `https://<internal-gearvia-host>/mcp` only through the GearVia HTTPS reverse proxy on
configured intranet or VPN networks. Set `MCP_ENABLED=true`, list those CIDRs in
`MCP_ALLOWED_CIDRS`, set `MCP_TRUSTED_PROXIES` only to the reverse-proxy container/network, and
never forward the backend port or MCP endpoint directly to the Internet. Nginx overwrites rather
than appends `X-Forwarded-For`, and GearVia trusts that header only from the configured proxy CIDR.

Each user issues a personal read-only token from My Profile. The plaintext is shown once; GearVia
stores only its SHA-256 hash. Revoking it or suspending the account blocks the next request.

Tool responses are item/byte bounded and every terminal tool outcome is audited. Rate and
concurrency limits are JVM-local in the current single-node release. Before adding a second
backend node, replace the limiter with a shared implementation (for example Redis or MySQL lease
counters); otherwise clients can consume the configured allowance once per node.

## Codex CLI

Store the one-time token in the local environment and register the Streamable HTTP endpoint:

```bash
export GEARVIA_MCP_TOKEN='paste-the-one-time-token'
codex mcp add gearvia --url https://gearvia.internal/mcp \
  --bearer-token-env-var GEARVIA_MCP_TOKEN
codex mcp get gearvia
```

The current Codex CLI exposes `--url` for Streamable HTTP and
`--bearer-token-env-var` so the secret is not written directly into `config.toml`.

## Claude Code

Claude Code supports a remote HTTP MCP server with an Authorization header. Prefer environment
expansion in the user-scoped JSON configuration so a shared project file does not contain a token:

```bash
export GEARVIA_MCP_TOKEN='paste-the-one-time-token'
claude mcp add-json gearvia \
  '{"type":"http","url":"https://gearvia.internal/mcp","headers":{"Authorization":"Bearer ${GEARVIA_MCP_TOKEN}"}}' \
  --scope user
claude mcp get gearvia
```

## Initial Tools

- `gearvia_list_groups`: groups visible to the token owner.
- `gearvia_list_tasks`: bounded tasks in a group after membership authorization.
- `gearvia_get_task`: one task after membership authorization.

The first release intentionally exposes read-only tools. It does not expose SQL, shell commands,
arbitrary HTTP, filesystem paths, or direct database access.

## Protocol and Security References

- MCP Streamable HTTP transport: <https://modelcontextprotocol.io/specification/2025-06-18/basic/transports>
- MCP tool schema and calls: <https://modelcontextprotocol.io/specification/2025-06-18/server/tools>
- Claude Code remote MCP configuration: <https://docs.anthropic.com/en/docs/claude-code/mcp>
