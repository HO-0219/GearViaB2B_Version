export const LIVE_UPDATE_EVENT = 'app:live-update';

export type LiveUpdate = {
  notificationId?: number;
  notificationType?: string;
  groupId?: number;
  taskId?: number;
  commentId?: number;
  url?: string;
  tag?: string;
};

export function publishLiveUpdate(value: LiveUpdate) {
  window.dispatchEvent(new CustomEvent<LiveUpdate>(LIVE_UPDATE_EVENT, { detail: value }));
  window.dispatchEvent(new Event('notifications:refresh'));
}

export function subscribeToLiveUpdates(listener: (value: LiveUpdate) => void) {
  const handler = (event: Event) => listener((event as CustomEvent<LiveUpdate>).detail ?? {});
  window.addEventListener(LIVE_UPDATE_EVENT, handler);
  return () => window.removeEventListener(LIVE_UPDATE_EVENT, handler);
}
