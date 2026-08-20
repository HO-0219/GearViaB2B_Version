import { LiveUpdate, publishLiveUpdate } from './liveUpdates';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

let installPrompt: BeforeInstallPromptEvent | undefined;
let registration: ServiceWorkerRegistration | undefined;
let reloading = false;
let updateRequested = false;

export function isPwaInstallAvailable() { return Boolean(installPrompt); }
export function isPwaUpdateAvailable() { return Boolean(registration?.waiting); }
export function isRunningStandalone() {
  return window.matchMedia('(display-mode: standalone)').matches
    || ('standalone' in navigator && Boolean((navigator as Navigator & { standalone?: boolean }).standalone));
}

export function registerPwa() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;

  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    installPrompt = event as BeforeInstallPromptEvent;
    window.dispatchEvent(new Event('pwa-install-available'));
  });
  window.addEventListener('appinstalled', () => {
    installPrompt = undefined;
    window.dispatchEvent(new Event('pwa-installed'));
  });
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (updateRequested && !reloading) {
      reloading = true;
      window.location.reload();
    }
  });
  navigator.serviceWorker.addEventListener('message', (event) => {
    if (event.data?.type !== 'PUSH_RECEIVED') return;
    publishLiveUpdate((event.data.data ?? {}) as LiveUpdate);
  });

  window.addEventListener('load', async () => {
    try {
      registration = await navigator.serviceWorker.register('/sw.js');
      if (registration.waiting) window.dispatchEvent(new Event('pwa-update-available'));
      registration.addEventListener('updatefound', () => {
        const worker = registration?.installing;
        worker?.addEventListener('statechange', () => {
          if (worker.state === 'installed' && navigator.serviceWorker.controller) {
            window.dispatchEvent(new Event('pwa-update-available'));
          }
        });
      });
    } catch {
      // 앱 자체 실행은 Service Worker 등록 실패와 독립적으로 유지한다.
    }
  });
}

export async function promptPwaInstall() {
  if (!installPrompt) return false;
  const prompt = installPrompt;
  installPrompt = undefined;
  await prompt.prompt();
  const choice = await prompt.userChoice;
  if (choice.outcome === 'accepted') window.dispatchEvent(new Event('pwa-installed'));
  else window.dispatchEvent(new Event('pwa-install-available'));
  return choice.outcome === 'accepted';
}

export function activatePwaUpdate() {
  updateRequested = true;
  registration?.waiting?.postMessage({ type: 'SKIP_WAITING' });
}

export async function pwaRegistration() {
  if (!('serviceWorker' in navigator)) return undefined;
  return registration ?? navigator.serviceWorker.ready;
}
