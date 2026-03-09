const CACHE = 'coreclash-web-v2';
const ASSETS = ['./','./index.html','./src/styles.css','./src/app.js','./manifest.webmanifest','./assets/icons/icon.svg'];

self.addEventListener('install', (event) => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS))));
self.addEventListener('activate', (event) => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))));
self.addEventListener('fetch', (event) => {
  event.respondWith(caches.match(event.request).then(cached => {
    const network = fetch(event.request).then(response => {
      caches.open(CACHE).then(cache => cache.put(event.request, response.clone()));
      return response;
    }).catch(() => cached);
    return cached || network;
  }));
});
