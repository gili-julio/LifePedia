// Service worker do app instalável. Só guarda a página offline: nada da API (dados sensíveis) nem das telas.
const CACHE = 'lifepedia-offline-v1'
const OFFLINE = ['/offline.html', '/offline.js', '/favicon.svg']

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(OFFLINE)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

// Navegação vai sempre à rede; sem conexão, mostra a página offline. Os demais pedidos não passam por aqui.
self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match('/offline.html')))
    return
  }
  const url = new URL(request.url)
  if (url.origin === self.location.origin && OFFLINE.includes(url.pathname)) {
    event.respondWith(fetch(request).catch(() => caches.match(url.pathname)))
  }
})
