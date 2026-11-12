import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { AddressRequest, CepResult, Delivery, DeliveryAddress, DeliveryRequest, DeliverySummary } from './types'

const BASE = '/api/deliveries'

/** Chaves `['deliveries', ...]`; mutações invalidam o módulo e as notificações. */
function useInvalidate() {
  const client = useQueryClient()
  return () => {
    void client.invalidateQueries({ queryKey: ['deliveries'] })
    void client.invalidateQueries({ queryKey: ['notifications'] })
  }
}

/** Confere de novo a cada 5 min enquanto a tela está aberta (o servidor sincroniza a cada hora). */
export function useDeliveries(archived: boolean) {
  return useQuery({
    queryKey: ['deliveries', 'list', archived],
    queryFn: () => api<Delivery[]>(BASE, { query: { archived: archived ? 'true' : undefined } }),
    refetchInterval: 5 * 60_000,
  })
}

export function useDelivery(id: string) {
  return useQuery({ queryKey: ['deliveries', 'detail', id], queryFn: () => api<Delivery>(`${BASE}/${id}`), retry: false })
}

export function useDeliverySummary(enabled = true) {
  return useQuery({ queryKey: ['deliveries', 'summary'], queryFn: () => api<DeliverySummary>(`${BASE}/summary`), enabled })
}

export function useAddresses() {
  return useQuery({ queryKey: ['deliveries', 'addresses'], queryFn: () => api<DeliveryAddress[]>(`${BASE}/addresses`) })
}

export function useSaveDelivery() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, ...body }: DeliveryRequest & { id?: string }) =>
      api<Delivery>(id ? `${BASE}/${id}` : BASE, { method: id ? 'PUT' : 'POST', body }),
    onSuccess: invalidate,
  })
}

export function useArchiveDelivery() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, archived }: { id: string; archived: boolean }) =>
      api<Delivery>(`${BASE}/${id}/archive`, { method: 'PUT', body: { archived } }),
    onSuccess: invalidate,
  })
}

export function useRefreshDelivery() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => api<Delivery>(`${BASE}/${id}/refresh`, { method: 'POST' }), onSuccess: invalidate })
}

export function useDeleteDelivery() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => api<void>(`${BASE}/${id}`, { method: 'DELETE' }), onSuccess: invalidate })
}

export function useDeliveryImage() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, image }: { id: string; image: Blob | null }) => {
      if (!image) return api<Delivery>(`${BASE}/${id}/image`, { method: 'DELETE' })
      const form = new FormData()
      form.append('file', image, 'item.jpg')
      return api<Delivery>(`${BASE}/${id}/image`, { method: 'PUT', body: form })
    },
    onSuccess: invalidate,
  })
}

export function useSaveAddress() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, ...body }: AddressRequest & { id?: string }) =>
      api<DeliveryAddress>(id ? `${BASE}/addresses/${id}` : `${BASE}/addresses`, { method: id ? 'PUT' : 'POST', body }),
    onSuccess: invalidate,
  })
}

export function useDeleteAddress() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => api<void>(`${BASE}/addresses/${id}`, { method: 'DELETE' }), onSuccess: invalidate })
}

/** Busca o endereço pelo CEP (8 dígitos) na API, que consulta o ViaCEP. */
export function lookupCep(cep: string) {
  return api<CepResult>(`${BASE}/cep/${cep}`)
}
