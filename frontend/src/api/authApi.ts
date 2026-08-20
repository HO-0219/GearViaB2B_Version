import { request, sessionClientHeaders } from './client';

export type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number };
export type MeResponse = { userId: number; username: string; email: string; name: string; role: string };
export type DeviceSessionResponse = {
  sessionId: string; deviceName: string; clientMode: 'WEB' | 'PWA'; ipAddress: string;
  createdAt: string; lastUsedAt: string; expiresAt: string; current: boolean;
};

export const authApi = {
  login: (username: string, password: string, mfaCode?: string) =>
    request<TokenResponse>('/auth/login', {
      method: 'POST',
      headers: sessionClientHeaders(),
      body: JSON.stringify({ username, password, mfaCode: mfaCode || undefined }),
    }),
  demo: () => request<TokenResponse>('/auth/demo-session', { method: 'POST' }),
  refresh: () => request<TokenResponse>('/auth/refresh', {
    method: 'POST', headers: sessionClientHeaders(),
  }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  logoutAll: () => request<void>('/auth/logout-all', { method: 'POST' }, true),
  me: () => request<MeResponse>('/auth/me', {}, true),
  sessions: () => request<{ sessions: DeviceSessionResponse[] }>('/auth/sessions', {}, true),
  logoutSession: (sessionId: string) =>
    request<void>(`/auth/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' }, true),
};
