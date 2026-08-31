import { request } from './client';

export type NotificationType = 'TASK_REQUESTED' | 'TASK_ASSIGNED' | 'TASK_STATUS_CHANGED' | 'TASK_DUE_SOON'
  | 'COMMENT_CREATED' | 'COMMENT_MENTIONED' | 'SECURITY_NEW_DEVICE' | 'SECURITY_SESSION_REUSED'
  | 'ASSISTANT_MESSAGE' | 'CHAT_MESSAGE';

export type NotificationResponse = {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  actorUserId?: number;
  actorNickname?: string;
  groupId?: number;
  groupName?: string;
  taskId?: number;
  commentId?: number;
  targetUrl?: string;
  read: boolean;
  readAt?: string;
  createdAt: string;
};

export type NotificationPageResponse = {
  items: NotificationResponse[];
  nextCursor?: number;
  hasNext: boolean;
  unreadCount: number;
};

export type PushConfigResponse = { enabled: boolean; publicKey: string; consentAgreed: boolean };
export type PushSubscriptionRequest = { endpoint: string; p256dh: string; auth: string };

export const notificationApi = {
  list: (size = 20, cursor?: number, filters: { read?: string; groupId?: string; type?: string; period?: string } = {}) => { const params = new URLSearchParams({ size: String(size) }); if (cursor) params.set('cursor', String(cursor)); Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); }); return request<NotificationPageResponse>(`/notifications?${params}`, {}, true); },
  read: (notificationId: number) => request<NotificationResponse>(`/notifications/${notificationId}/read`, {
    method: 'PATCH',
  }, true),
  readAll: () => request<{ updatedCount: number }>('/notifications/read-all', {
    method: 'PATCH',
  }, true),
  delete: (notificationId: number) => request<void>(`/notifications/${notificationId}`, {
    method: 'DELETE',
  }, true),
  pushConfig: () => request<PushConfigResponse>('/notifications/push-config', {}, true),
  subscribePush: (body: PushSubscriptionRequest) => request<void>('/notifications/push-subscriptions', {
    method: 'POST', body: JSON.stringify(body),
  }, true),
  unsubscribePush: (endpoint: string) => request<void>('/notifications/push-subscriptions', {
    method: 'DELETE', body: JSON.stringify({ endpoint }),
  }, true),
};
