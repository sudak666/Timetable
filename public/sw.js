/*
 * Service worker: офлайн-оболонка + web push.
 * • Хешовані файли збірки (assets/назва-ХЕШ.js|css|woff2) незмінні → «спершу кеш», кеш обмежений за кількістю.
 * • Решта (theme-init.js, manifest.json, емодзі, іконки) → з кешу одразу, але оновлюється у фоні,
 *   щоб зміна після релізу дійшла з наступним відкриттям (аудит 14.11.2026, Б9).
 */
// v4 (23.11.2026): нова іконка «12» замість рюкзака — старий кеш з іконками видаляється при активації.
const CACHE = 'timetable-v4';
const ASSETS = 'timetable-assets-v1';
const SHELL = ['./', 'manifest.json', 'assets/icon-192.png?v=2'];
/** Скільки хешованих файлів тримати: кілька релізів, далі найстаріші видаляються. */
const MAX_ASSETS = 80;
const HASHED = /\/assets\/[^/]+-[A-Za-z0-9_-]{8}\.[a-z0-9]+$/;

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE && k !== ASSETS).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

/** Ключі кешу йдуть у порядку додавання — прибираємо найстаріші понад ліміт. */
async function trim(name, max) {
  const c = await caches.open(name), keys = await c.keys();
  await Promise.all(keys.slice(0, Math.max(0, keys.length - max)).map((k) => c.delete(k)));
}

self.addEventListener('fetch', (e) => {
  const r = e.request, u = new URL(r.url);
  if (r.method !== 'GET' || u.origin !== location.origin) return;
  if (r.mode === 'navigate') {
    // Оболонкою кешуємо лише головну сторінку — інакше, напр., privacy.html підмінив би застосунок офлайн.
    const shell = u.pathname.endsWith('/') || u.pathname.endsWith('/index.html');
    e.respondWith(fetch(r).then((res) => {
      if (shell && res.ok) { const cp = res.clone(); caches.open(CACHE).then((c) => c.put('./', cp)); }
      return res;
    }).catch(() => caches.match('./')));
    return;
  }
  if (HASHED.test(u.pathname)) {
    e.respondWith(caches.match(r).then((hit) => hit || fetch(r).then((res) => {
      if (res.ok) { const cp = res.clone(); e.waitUntil(caches.open(ASSETS).then((c) => c.put(r, cp)).then(() => trim(ASSETS, MAX_ASSETS))); }
      return res;
    })));
    return;
  }
  // Нехешовані: відповідь із кешу (швидко й офлайн), паралельно — свіжа версія в кеш.
  const fresh = fetch(r).then((res) => {
    if (res.ok) { const cp = res.clone(); caches.open(CACHE).then((c) => c.put(r, cp)); }
    return res;
  });
  e.waitUntil(fresh.catch(() => undefined));
  e.respondWith(caches.match(r).then((hit) => hit || fresh));
});
self.addEventListener('push', (e) => {
  let d = {};
  try { d = e.data.json(); } catch { d = { title: 'Дванадцятка', body: e.data ? e.data.text() : '' }; }
  e.waitUntil(self.registration.showNotification(d.title || 'Дванадцятка', { body: d.body || '', tag: d.tag, data: { url: typeof d.url === 'string' && d.url.startsWith('#') ? d.url : '#grades' }, icon: 'assets/icon-192.png?v=2', badge: 'assets/icon-192.png?v=2' }));
});
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.url) || '#grades';
  e.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then((ws) => {
    const w = ws.find((x) => x.url.startsWith(self.registration.scope));
    if (!w) return clients.openWindow('./' + url);
    w.postMessage({ nav: url });
    return w.focus();
  }));
});
