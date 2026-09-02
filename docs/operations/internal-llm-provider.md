# Internal LLM Provider Operations

GearVia can route the AI assistant, weekly reports, and document embeddings to either
OpenAI or an internal server that implements the OpenAI-compatible `/v1` API.

## Network policy

- `OPENAI` is accepted only with `https://api.openai.com` and an explicit external-access switch.
- `INTERNAL_OPENAI_COMPATIBLE` resolves the configured host during validation and accepts it only
  when every resolved address is loopback, link-local, RFC1918, or IPv6 unique-local.
- URLs containing credentials, query strings, or fragments are rejected.
- Internet egress should remain blocked at the host firewall. If OpenAI is required, route the
  approved destination through the corporate VPN/firewall and enable the switch in GearVia.
- Credential-bearing internal endpoints require HTTPS. Their certificate chain must be trusted by
  the backend JVM/container trust store.

## Configure and verify

1. Open **Admin > AI settings**.
2. Select the provider and enter the `/v1` base URL.
3. Enter the exact chat and embedding model identifiers exposed by the server.
4. Set a request timeout between 1 and 120 seconds. Start at 30 seconds for chat and increase
   only after measuring the internal model queue.
5. Add a credential if the server requires one. It is encrypted with
   `ADMIN_MFA_ENCRYPTION_KEY_BASE64`; it is never returned by the API.
6. Save, then run **Test connection**. A failed test does not silently switch providers.

The same immutable SDK client is rebuilt after a successful settings save, so no application
restart is required. Existing document vectors remain tagged with their original embedding model;
documents must be re-indexed when the embedding model changes.

## Compatibility boundary

The internal server must support OpenAI-compatible models, Responses API calls, structured
output/tool calls used by the assistant and reports, and embeddings. A server that only supports
Chat Completions is not sufficient for all GearVia AI features.
