import { request } from './client';

export type AssistantTurn = { role: 'user' | 'assistant'; content: string };
export type AssistantChatResponse = {
  message: string;
  pendingActionId?: number;
  actionType?: string;
  actionSummary?: string;
  expiresAt?: string;
};
export type AssistantActionResponse = {
  actionId: number;
  status: 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';
  message: string;
  targetUrl?: string;
  inviteUrl?: string;
  selectedGroupId?: number;
};
export type AssistantIndexResponse = {
  indexed: number; skipped: number; removed: number; unsupported: number; failures: string[];
};
export type AssistantMessageResponse = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  actionId?: number;
  actionType?: string;
  actionSummary?: string;
  actionExpiresAt?: string;
  actionStatus?: 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';
  createdAt: string;
};

export const assistantApi = {
  chat: (groupId: number, message: string) =>
    request<AssistantChatResponse>('/assistant/messages', {
      method: 'POST', body: JSON.stringify({ groupId, message }),
    }, true),
  messages: (groupId: number) => request<AssistantMessageResponse[]>(
    `/assistant/messages?groupId=${groupId}`, {}, true),
  confirm: (actionId: number) => request<AssistantActionResponse>(
    `/assistant/actions/${actionId}/confirm`, { method: 'POST' }, true),
  cancel: (actionId: number) => request<AssistantActionResponse>(
    `/assistant/actions/${actionId}/cancel`, { method: 'POST' }, true),
  reindex: (groupId: number) => request<AssistantIndexResponse>(
    `/assistant/reindex?groupId=${groupId}`, { method: 'POST' }, true),
};
