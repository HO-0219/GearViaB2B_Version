import { request, serviceUrl } from './client';

export type McpToken = {
  id: number; label: string; token?: string | null; scope: string; createdAt?: string;
  expiresAt: string; lastUsedAt?: string | null; lastIp?: string | null;
  clientLabel?: string | null; revokedAt?: string | null;
};

export const mcpApi = {
  list: () => request<McpToken[]>('/me/mcp-tokens', {}, true),
  create: (label: string, expiryDays: number) => request<McpToken>('/me/mcp-tokens', {
    method: 'POST', body: JSON.stringify({ label, expiryDays }),
  }, true),
  revoke: (id: number) => request<void>(`/me/mcp-tokens/${id}`, { method: 'DELETE' }, true),
  endpoint: () => serviceUrl('/mcp'),
};
