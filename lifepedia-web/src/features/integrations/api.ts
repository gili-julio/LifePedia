import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  CalendarEvent,
  IntegrationAccount,
  IntegrationOverview,
  IntegrationSummary,
  SyncEntry,
  TokenInput,
} from './types'

const BASE = '/api/integrations'

/** Chaves sob 'integrations'; mutação também invalida o Dev (repositórios e reuniões vêm das contas) e as conquistas. */
const keys = {
  all: ['integrations'] as const,
  overview: ['integrations', 'overview'] as const,
  summary: ['integrations', 'summary'] as const,
  history: (id: string) => ['integrations', 'history', id] as const,
  calendar: (from: string, to: string) => ['integrations', 'calendar', from, to] as const,
}

function useIntegrationMutation<TVars, TResult = void>(fn: (vars: TVars) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      client.invalidateQueries({ queryKey: keys.all })
      client.invalidateQueries({ queryKey: ['dev'] })
      client.invalidateQueries({ queryKey: ['friends'] })
      client.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useIntegrations() {
  return useQuery({ queryKey: keys.overview, queryFn: () => api<IntegrationOverview>(BASE) })
}

export function useIntegrationSummary(enabled = true) {
  return useQuery({ queryKey: keys.summary, queryFn: () => api<IntegrationSummary>(`${BASE}/summary`), enabled })
}

export function useConnectToken() {
  return useIntegrationMutation(({ provider, ...body }: TokenInput & { provider: 'GITLAB' | 'GITHUB' }) =>
    api<IntegrationAccount>(`${BASE}/${provider.toLowerCase()}`, { method: 'POST', body }))
}

export function useConnectDuolingo() {
  return useIntegrationMutation((body: { username: string }) => api<IntegrationAccount>(`${BASE}/duolingo`, { method: 'POST', body }))
}

/** Pede o endereço de consentimento do Google; quem chama leva o navegador até lá. */
export function useGoogleStart() {
  return useMutation({ mutationFn: () => api<{ url: string }>(`${BASE}/google/start`, { method: 'POST' }) })
}

export function useLabelAccount() {
  return useIntegrationMutation(({ id, ...body }: { id: string; nickname: string; color: string }) =>
    api<IntegrationAccount>(`${BASE}/${id}`, { method: 'PUT', body }))
}

export function useReconnect() {
  return useIntegrationMutation(({ id, token }: { id: string; token: string }) =>
    api<IntegrationAccount>(`${BASE}/${id}/token`, { method: 'PUT', body: { token } }))
}

export function useSyncAccount() {
  return useIntegrationMutation((id: string) => api<IntegrationAccount>(`${BASE}/${id}/sync`, { method: 'POST' }))
}

export function useSyncHistory(id: string | null) {
  return useQuery({ queryKey: keys.history(id ?? ''), queryFn: () => api<SyncEntry[]>(`${BASE}/${id}/syncs`), enabled: !!id })
}

export function useDisconnect() {
  return useIntegrationMutation((id: string) => api<void>(`${BASE}/${id}`, { method: 'DELETE' }))
}

/** Eventos do Google Agenda de todas as contas (dias `yyyy-MM-dd`, até 62). */
export function useCalendarEvents(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: keys.calendar(from, to),
    queryFn: () => api<CalendarEvent[]>(`${BASE}/calendar?from=${from}&to=${to}`),
    enabled,
  })
}
