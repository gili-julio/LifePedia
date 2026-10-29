import { useTheme } from 'next-themes'
import { useEffect, useSyncExternalStore } from 'react'

type InstallPromptEvent = Event & { prompt: () => Promise<{ outcome: 'accepted' | 'dismissed' }> }

/** Cor da barra do sistema no app instalado: o fundo do tema (`--background`). */
const THEME_COLORS = { light: '#f7f7f5', dark: '#070707' }
const RELOADED_KEY = 'lifepedia.chunkReloadAt'

let installPrompt: InstallPromptEvent | null = null
const listeners = new Set<() => void>()

type PwaHandlers = {
  /** Tocou numa notificação com o app aberto: ir para a tela dela sem recarregar */
  navigate: (path: string) => void
  /** Chegou um push: atualizar notificações/chat na tela */
  push: () => void
}

/** Registra o service worker (só no build), guarda o convite de instalação e recarrega quando a versão publicada muda. */
export function setupPwa(handlers: PwaHandlers) {
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault()
    installPrompt = event as InstallPromptEvent
    notify()
  })
  window.addEventListener('appinstalled', () => {
    installPrompt = null
    notify()
  })
  // Deploy novo apaga os arquivos antigos: a tela aberta há tempo (comum no app instalado) recarrega uma vez.
  window.addEventListener('vite:preloadError', (event) => {
    if (Date.now() - Number(readSession(RELOADED_KEY) ?? 0) < 10_000) return
    event.preventDefault()
    writeSession(RELOADED_KEY, String(Date.now()))
    location.reload()
  })
  if (import.meta.env.PROD && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => undefined)
    })
    navigator.serviceWorker.addEventListener('message', (event: MessageEvent<{ type?: string; url?: string }>) => {
      if (event.data?.type === 'push') handlers.push()
      if (event.data?.type === 'navigate' && event.data.url?.startsWith('/')) handlers.navigate(event.data.url)
    })
  }
}

/** Instalar o app: convite do navegador (Chrome/Edge/Android) ou instruções (iPhone/iPad); nada se já instalado. */
export function useInstallApp() {
  const prompt = useSyncExternalStore(subscribe, () => installPrompt)
  const standalone = useSyncExternalStore(subscribeStandalone, isStandalone)
  return {
    canPrompt: !standalone && prompt !== null,
    needsIosSteps: !standalone && prompt === null && isIos(),
    async install() {
      if (!prompt) return
      const { outcome } = await prompt.prompt()
      if (outcome === 'accepted') {
        installPrompt = null
        notify()
      }
    },
  }
}

/** Barra do sistema com a cor do tema escolhido no app (o `index.html` só sabe o do sistema). */
export function useThemeColor() {
  const { resolvedTheme } = useTheme()
  useEffect(() => {
    const color = THEME_COLORS[resolvedTheme === 'dark' ? 'dark' : 'light']
    document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => meta.setAttribute('content', color))
  }, [resolvedTheme])
}

/** App aberto pelo ícone instalado (tela cheia). */
export function isStandalone() {
  return matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true
}

/** iPhone/iPad (iPad com iPadOS se apresenta como Mac). */
export function isIos() {
  return /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

function notify() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function subscribeStandalone(listener: () => void) {
  const query = matchMedia('(display-mode: standalone)')
  query.addEventListener('change', listener)
  return () => query.removeEventListener('change', listener)
}

function readSession(key: string) {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

function writeSession(key: string, value: string) {
  try {
    sessionStorage.setItem(key, value)
  } catch {
    // storage indisponível
  }
}
