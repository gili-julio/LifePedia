import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { CreateRequest, ManualPriceRequest, PriceSettings, WatchDetail, WatchSummary } from './types'

const BASE = '/api/prices'

/** Chaves `['prices', ...]`; mutações também recarregam o planejamento (o valor do item pode mudar). */
function useInvalidate() {
  const client = useQueryClient()
  return () => {
    void client.invalidateQueries({ queryKey: ['prices'] })
    void client.invalidateQueries({ queryKey: ['planning'] })
  }
}

/** Sem grupo: pessoais e dos grupos da pessoa; com grupo: só os dele. */
export function usePriceWatches(groupId?: string, enabled = true) {
  return useQuery({
    queryKey: ['prices', 'list', groupId ?? null],
    queryFn: () => api<WatchSummary[]>(BASE, { query: { groupId } }),
    enabled,
  })
}

export function usePriceWatch(id: string) {
  return useQuery({ queryKey: ['prices', 'detail', id], queryFn: () => api<WatchDetail>(`${BASE}/${id}`), retry: false })
}

function useWatchMutation<T>(fn: (variables: T) => Promise<WatchDetail>) {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: fn, onSuccess: invalidate })
}

export function useCreateWatch() {
  return useWatchMutation((body: CreateRequest) => api<WatchDetail>(BASE, { method: 'POST', body }))
}

export function useUpdateWatch() {
  return useWatchMutation(({ id, name, settings }: { id: string; name: string; settings: PriceSettings }) =>
    api<WatchDetail>(`${BASE}/${id}`, { method: 'PUT', body: { name, settings } }))
}

export function useRefreshWatch() {
  return useWatchMutation((id: string) => api<WatchDetail>(`${BASE}/${id}/refresh`, { method: 'POST' }))
}

export function useAddWatchLink() {
  return useWatchMutation(({ id, url }: { id: string; url: string }) =>
    api<WatchDetail>(`${BASE}/${id}/links`, { method: 'POST', body: { url } }))
}

export function useRemoveWatchLink() {
  return useWatchMutation(({ id, linkId }: { id: string; linkId: string }) =>
    api<WatchDetail>(`${BASE}/${id}/links/${linkId}`, { method: 'DELETE' }))
}

export function useAddWatchPrice() {
  return useWatchMutation(({ id, ...body }: ManualPriceRequest & { id: string }) =>
    api<WatchDetail>(`${BASE}/${id}/prices`, { method: 'POST', body }))
}

export function useRemoveWatchPrice() {
  return useWatchMutation(({ id, pointId }: { id: string; pointId: string }) =>
    api<WatchDetail>(`${BASE}/${id}/prices/${pointId}`, { method: 'DELETE' }))
}

export function useDeleteWatch() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => api<void>(`${BASE}/${id}`, { method: 'DELETE' }), onSuccess: invalidate })
}
