import { notificationApi } from '../api/notificationApi';
import { isRunningStandalone, pwaRegistration } from './pwa';

export type PushSetupState = 'hidden' | 'prompt' | 'denied' | 'enabled' | 'error';
export type PushSetupResult = { state: PushSetupState; consentAgreed: boolean };

export async function inspectPushNotifications(): Promise<PushSetupResult> {
  if (!isRunningStandalone() || !supportsPush()) return { state: 'hidden', consentAgreed: false };
  const config = await notificationApi.pushConfig();
  if (!config.enabled || !config.publicKey) return { state: 'hidden', consentAgreed: config.consentAgreed };
  if (Notification.permission === 'denied') return { state: 'denied', consentAgreed: config.consentAgreed };
  const registration = await pwaRegistration();
  if (!registration) return { state: 'hidden', consentAgreed: config.consentAgreed };
  const existing = await registration.pushManager.getSubscription();
  if (existing && matchesServerKey(existing, config.publicKey)) {
    await saveSubscription(existing);
    return { state: 'enabled', consentAgreed: true };
  }
  if (existing) await discardSubscription(existing);
  if (Notification.permission === 'granted') {
    await createSubscription(registration, config.publicKey);
    return { state: 'enabled', consentAgreed: true };
  }
  return { state: 'prompt', consentAgreed: config.consentAgreed };
}

export async function enablePushNotifications(): Promise<PushSetupResult> {
  if (!supportsPush()) return { state: 'hidden', consentAgreed: false };
  const config = await notificationApi.pushConfig();
  if (!config.enabled || !config.publicKey) return { state: 'hidden', consentAgreed: config.consentAgreed };
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    return { state: permission === 'denied' ? 'denied' : 'prompt', consentAgreed: config.consentAgreed };
  }
  const registration = await pwaRegistration();
  if (!registration) return { state: 'error', consentAgreed: config.consentAgreed };
  const existing = await registration.pushManager.getSubscription();
  if (existing && matchesServerKey(existing, config.publicKey)) await saveSubscription(existing);
  else {
    if (existing) await discardSubscription(existing);
    await createSubscription(registration, config.publicKey);
  }
  return { state: 'enabled', consentAgreed: true };
}

export async function disablePushForCurrentDevice() {
  if (!supportsPush()) return;
  // navigator.serviceWorker.ready never resolves if registration failed or never
  // finished, which would otherwise hang this indefinitely -- and callers (e.g.
  // logout) await this before doing anything else. Give up instead of blocking them.
  const registration = await Promise.race([pwaRegistration(), timeout(3000)]);
  if (!registration) return;
  const subscription = await registration.pushManager.getSubscription();
  if (!subscription) return;
  try { await notificationApi.unsubscribePush(subscription.endpoint); }
  finally { await subscription.unsubscribe(); }
}

function timeout(ms: number): Promise<undefined> {
  return new Promise((resolve) => setTimeout(() => resolve(undefined), ms));
}

function supportsPush() {
  return 'Notification' in window && 'PushManager' in window && 'serviceWorker' in navigator;
}

// 서버 VAPID 키가 바뀌면 남아 있는 구독으로는 발송이 403으로 영구 실패한다. 키가 다르면 버리고 다시 만든다.
function matchesServerKey(subscription: PushSubscription, publicKey: string) {
  const applied = subscription.options.applicationServerKey;
  if (!applied) return false;
  const current = base64UrlToBytes(publicKey);
  const stored = new Uint8Array(applied);
  return stored.length === current.length && stored.every((byte, index) => byte === current[index]);
}

async function discardSubscription(subscription: PushSubscription) {
  try { await notificationApi.unsubscribePush(subscription.endpoint); }
  catch { /* 서버 정리는 실패해도 발송 시 403 응답으로 정리된다. */ }
  await subscription.unsubscribe();
}

async function createSubscription(registration: ServiceWorkerRegistration, publicKey: string) {
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: base64UrlToBytes(publicKey) as BufferSource,
  });
  await saveSubscription(subscription);
}

async function saveSubscription(subscription: PushSubscription) {
  const value = subscription.toJSON();
  if (!value.endpoint || !value.keys?.p256dh || !value.keys.auth) throw new Error('Invalid push subscription');
  await notificationApi.subscribePush({
    endpoint: value.endpoint,
    p256dh: value.keys.p256dh,
    auth: value.keys.auth,
  });
}

function base64UrlToBytes(value: string) {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - value.length % 4) % 4);
  const decoded = window.atob(padded);
  return Uint8Array.from(decoded, character => character.charCodeAt(0));
}
