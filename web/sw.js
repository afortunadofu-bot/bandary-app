// Bandary PWA service worker。
//
// 只快取自家的 app shell（HTML / manifest / icon）。刻意**不快取 Firebase 的 API 請求**——
// 雙向同步靠的是即時讀寫，快取住會讓網頁顯示舊資料、還可能把過期內容寫回雲端。
// Firestore SDK 自己有離線持久化，離線讀寫交給它處理就好。
const CACHE = 'bandary-shell-v1';
const SHELL = ['/', '/index.html', '/manifest.json'];

self.addEventListener('install', (e) => {
  // 新版本裝好就直接接管，不用等所有分頁關閉——否則使用者會一直看到舊版。
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  // 只處理同源的 GET；Firebase / Google 的網域一律直接放行。
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;

  // app shell 走 network-first：有網路時永遠拿最新版，沒網路才回退快取。
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      })
      .catch(() => caches.match(e.request).then((r) => r || caches.match('/index.html')))
  );
});
