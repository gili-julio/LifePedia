// Service worker do app instalável: página offline e notificações push. Não guarda nada da API (dados sensíveis) nem das telas.
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

// Push: com o app em primeiro plano só avisa a tela (ela atualiza sozinha); senão mostra a notificação.
self.addEventListener('push', (event) => {
  const data = event.data ? event.data.json() : {}
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    windows.forEach((client) => client.postMessage({ type: 'push' }))
    if (data.tag !== 'test' && windows.some((client) => client.focused)) return
    await self.registration.showNotification(data.title || 'LifePedia', {
      body: data.body,
      tag: data.tag,
      renotify: Boolean(data.tag),
      icon: '/icon-192.png',
      badge: '/badge-96.png',
      lang: 'pt-BR',
      data: { url: data.url || '/' },
    })
  })())
})

// Tocar abre a tela da notificação: na janela já aberta (sem recarregar) ou numa nova.
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.url || '/', self.location.origin)
  if (url.origin !== self.location.origin) return
  const path = url.pathname + url.search
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const client = windows.find((c) => c.focused) || windows[0]
    if (!client) return self.clients.openWindow(path)
    await client.focus()
    client.postMessage({ type: 'navigate', url: path })
  })())
})
