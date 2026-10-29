import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { api, ApiError } from '@/lib/api'
import { isIos, isStandalone } from '@/lib/pwa'

/**
 * Push neste aparelho: `off-server` (API sem chaves), `unsupported`, `install` (iPhone fora do app instalado),
 * `blocked` (permissão negada), `off` ou `on`.
 */
export type PushState = 'loading' | 'off-server' | 'unsupported' | 'install' | 'blocked' | 'off' | 'on'

const keys = {
  config: ['notifications', 'push', 'config'] as const,
  device: ['notifications', 'push', 'device'] as const,
}

/** Estado do push no aparelho; ao abrir o app religa a inscrição à sessão atual (só se já era desta conta). */
export function useDevicePush() {
  const config = useQuery({
    queryKey: keys.config,
    queryFn: () => api<{ publicKey: string | null }>('/api/push/config'),
    staleTime: Infinity,
  })
  const publicKey = config.data?.publicKey ?? null
  const device = useQuery({
    queryKey: keys.device,
    queryFn: () => syncDevice(publicKey!),
    enabled: !!publicKey,
    staleTime: Infinity,
  })
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)

  const state: PushState = config.isPending ? 'loading'
    : !publicKey ? 'off-server'
    : device.data ?? 'loading'

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível mudar as notificações deste aparelho.')
    } finally {
      await queryClient.invalidateQueries({ queryKey: keys.device })
      setBusy(false)
    }
  }

  return {
    state,
    busy,
    enable: () => run(() => enable(publicKey!)),
    disable: () => run(disable),
  }
}

async function syncDevice(publicKey: string): Promise<PushState> {
  const registration = await serviceWorker()
  if (!registration) return isIos() && !isStandalone() ? 'install' : 'unsupported'
  if (Notification.permission === 'denied') return 'blocked'
  const subscription = await registration.pushManager.getSubscription()
  if (!subscription || Notification.permission !== 'granted' || !sameKey(subscription, publicKey)) return 'off'
  const { active } = await api<{ active: boolean }>('/api/push/subscription', { method: 'PUT', body: body(subscription, true) })
  return active ? 'on' : 'off'
}

async function enable(publicKey: string) {
  const registration = await serviceWorker()
  if (!registration) return
  if (await Notification.requestPermission() !== 'granted') return
  let subscription = await registration.pushManager.getSubscription()
  if (subscription && !sameKey(subscription, publicKey)) {
    await subscription.unsubscribe()
    subscription = null
  }
  subscription ??= await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: decode(publicKey) })
  await api('/api/push/subscription', { method: 'PUT', body: body(subscription, false) })
}

async function disable() {
  const subscription = await (await serviceWorker())?.pushManager.getSubscription()
  if (!subscription) return
  await api('/api/push/subscription', { method: 'DELETE', body: { endpoint: subscription.endpoint } })
  await subscription.unsubscribe()
}

/** Service worker com push (só no build publicado; no `npm run dev` não há). */
async function serviceWorker() {
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) return null
  const registration = await navigator.serviceWorker.getRegistration()
  return registration ? navigator.serviceWorker.ready : null
}

function body(subscription: PushSubscription, auto: boolean) {
  const { keys: subscriptionKeys } = subscription.toJSON()
  return { endpoint: subscription.endpoint, keys: { p256dh: subscriptionKeys?.p256dh, auth: subscriptionKeys?.auth }, auto }
}

/** Inscrição feita com outra chave VAPID (chaves trocadas no servidor) precisa ser refeita. */
function sameKey(subscription: PushSubscription, publicKey: string) {
  const current = subscription.options.applicationServerKey
  if (!current) return false
  const expected = decode(publicKey)
  const bytes = new Uint8Array(current)
  return bytes.length === expected.length && bytes.every((byte, i) => byte === expected[i])
}

function decode(base64url: string) {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(base64url.length / 4) * 4, '=')
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
}
