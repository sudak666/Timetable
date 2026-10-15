// Синхронно до першого paint: тема й ігровий стиль без мерехтіння.
try {
  const r = document.documentElement, t = localStorage.getItem('theme');
  if (t === 'dark' || t === 'light') r.dataset.theme = t;
} catch { /* приватний режим */ }
