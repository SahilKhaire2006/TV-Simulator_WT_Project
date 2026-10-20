/**
 * Service Worker for Virtual TV Broadcast Simulator PWA Remote.
 *
 * Strategy:
 *  - App shell (HTML/JS/CSS): Cache-first with network fallback
 *  - API requests:            Network-first (always fresh)
 *  - Google Fonts:            Cache-first (long-lived)
 *
 * NOTE: When using vite-plugin-pwa, this file is extended / replaced by
 * the Workbox-generated SW. Keep this file as the manual fallback for
 * environments where the plugin is not used.
 */

const CACHE_NAME     = 'tv-remote-v1';
const API_CACHE_NAME = 'tv-api-v1';

const APP_SHELL = [
  '/',
  '/index.html',
  '/manifest.json',
  '/icon-192.png',
  '/icon-512.png',
];

// ── Install ────────────────────────────────────────────────────────────────
self.addEventListener('install', (event) => {
  console.log('[SW] Installing service worker…');
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[SW] Caching app shell');
      return cache.addAll(APP_SHELL);
    }).then(() => self.skipWaiting())
  );
});

// ── Activate ───────────────────────────────────────────────────────────────
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating service worker…');
  event.waitUntil(
    caches.keys().then((cacheNames) =>
      Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME && name !== API_CACHE_NAME)
          .map((name) => {
            console.log('[SW] Deleting old cache:', name);
            return caches.delete(name);
          })
      )
    ).then(() => self.clients.claim())
  );
});

// ── Fetch ──────────────────────────────────────────────────────────────────
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-GET and WebSocket requests
  if (request.method !== 'GET' || url.protocol === 'ws:' || url.protocol === 'wss:') {
    return;
  }

  // API requests → Network-first
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/tv-simulator/api/')) {
    event.respondWith(networkFirst(request, API_CACHE_NAME));
    return;
  }

  // Google Fonts → Cache-first
  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    event.respondWith(cacheFirst(request, 'google-fonts-v1'));
    return;
  }

  // App shell → Cache-first with network fallback
  event.respondWith(cacheFirst(request, CACHE_NAME));
});

// ── Strategy: Cache-first ──────────────────────────────────────────────────
async function cacheFirst(request, cacheName) {
  const cached = await caches.match(request);
  if (cached) return cached;

  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(cacheName);
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    // Offline fallback
    const fallback = await caches.match('/index.html');
    return fallback || new Response('Offline – please reconnect', { status: 503 });
  }
}

// ── Strategy: Network-first ────────────────────────────────────────────────
async function networkFirst(request, cacheName) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(cacheName);
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    const cached = await caches.match(request);
    return cached || new Response(JSON.stringify({ error: 'Offline' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}

// ── Push notification handler (future extension) ───────────────────────────
self.addEventListener('push', (event) => {
  const data = event.data ? event.data.json() : { title: 'TV Broadcast Alert', body: 'Channel update available' };
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body:  data.body,
      icon:  '/icon-192.png',
      badge: '/icon-192.png',
      tag:   'tv-broadcast',
      renotify: true,
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(clients.openWindow('/'));
});
