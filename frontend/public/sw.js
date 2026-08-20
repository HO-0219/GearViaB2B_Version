const CACHE_NAME = 'b2bgearvia-shell-v10';
const APP_SHELL = ['/app', '/manifest.webmanifest', '/icons/app-icon.svg', '/icons/app-icon-192.png', '/icons/app-icon-512.png', '/icons/app-icon-maskable-512.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('push', (event) => {
  let data = {};
  try { data = event.data?.json() ?? {}; } catch { data = { body: event.data?.text() ?? '' }; }
  const target = typeof data.url === 'string' && data.url.startsWith('/') ? data.url : '/notifications';
  const notification = self.registration.showNotification(data.title || 'B2BGearVia', {
    body: data.body || '새 알림이 도착했습니다.',
    icon: '/icons/app-icon-192.png',
    badge: '/icons/app-icon-192.png',
    tag: data.tag || 'b2bgearvia-notification',
    data: { url: target },
    silent: true,
    requireInteraction: true,
  });
  const updateOpenApps = self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => clients.forEach((client) => client.postMessage({ type: 'PUSH_RECEIVED', data })));
  event.waitUntil(Promise.all([notification, updateOpenApps]));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = new URL(event.notification.data?.url || '/notifications', self.location.origin).href;
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (clients) => {
    const exact = clients.find((client) => client.url === target);
    if (exact) return exact.focus();
    if (clients[0]) {
      await clients[0].navigate(target);
      return clients[0].focus();
    }
    return self.clients.openWindow(target);
  }));
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/uploads/')) {
    event.respondWith(fetch(request).catch(() => new Response(JSON.stringify({
      code: 'OFFLINE',
      message: '오프라인에서는 새 데이터를 불러오거나 변경할 수 없습니다.',
    }), { status: 503, headers: { 'Content-Type': 'application/json; charset=utf-8' } })));
    return;
  }

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(async () => (await caches.match(request)) || caches.match('/app')));
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => cached || fetch(request).then((response) => {
      if (response.ok) caches.open(CACHE_NAME).then((cache) => cache.put(request, response.clone()));
      return response;
    })),
  );
});
