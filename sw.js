const CACHE = 'timetable-v1';
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(['./', 'index.html', 'manifest.json', 'assets/icon.webp', 'assets/icon-192.png'])).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
// сторінка: спершу мережа, без мережі — кеш; статичні файли: кеш, потім мережа
self.addEventListener('fetch', e => {
  const r = e.request, u = new URL(r.url);
  if (r.method !== 'GET' || u.hostname.endsWith('supabase.co')) return;
  if (r.mode === 'navigate') {
    e.respondWith(fetch(r).then(res => { caches.open(CACHE).then(c => c.put('./', res.clone())); return res; }).catch(() => caches.match('./')));
    return;
  }
  e.respondWith(caches.match(r).then(hit => hit || fetch(r).then(res => {
    if (res.ok && (u.origin === location.origin || u.hostname.includes('jsdelivr') || u.hostname.includes('gstatic') || u.hostname.includes('googleapis'))) {
      const cp = res.clone(); caches.open(CACHE).then(c => c.put(r, cp));
    }
    return res;
  })));
});
self.addEventListener('push', e => {
  let d = {};
  try { d = e.data.json(); } catch { d = { title: 'Розклад', body: e.data?.text() }; }
  e.waitUntil(self.registration.showNotification(d.title || 'Розклад', { body: d.body || '', tag: d.tag, icon: 'assets/icon-192.png', badge: 'assets/icon-192.png' }));
});
self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(ws => {
    const w = ws.find(w => w.url.includes(self.registration.scope));
    return w ? w.focus() : clients.openWindow('./#grades');
  }));
});
