import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  DocumentRequest,
  DoneRequest,
  Expense,
  ExpenseRequest,
  FipeKind,
  FipeOption,
  FipePrice,
  ItemRequest,
  PlanRequest,
  Reading,
  RenewRequest,
  RevisionRequest,
  ServiceLog,
  ShareOptions,
  Shares,
  Template,
  VehicleDetail,
  VehicleList,
  VehicleRequest,
  VehicleRole,
  VehicleStats,
  VehicleSummary,
  VehicleType,
} from './types'

const BASE = '/api/vehicles'

/** Chaves `['vehicles', ...]`; mutações invalidam o módulo, as notificações e o financeiro (gastos viram lançamentos). */
function useInvalidate() {
  const client = useQueryClient()
  return () => {
    void client.invalidateQueries({ queryKey: ['vehicles'] })
    void client.invalidateQueries({ queryKey: ['notifications'] })
    void client.invalidateQueries({ queryKey: ['finance'] })
  }
}

export function useVehicles() {
  return useQuery({ queryKey: ['vehicles', 'list'], queryFn: () => api<VehicleList>(BASE) })
}

export function useVehicle(id: string) {
  return useQuery({ queryKey: ['vehicles', 'detail', id], queryFn: () => api<VehicleDetail>(`${BASE}/${id}`), retry: false })
}

export function useVehicleSummary(enabled = true) {
  return useQuery({ queryKey: ['vehicles', 'summary'], queryFn: () => api<VehicleSummary>(`${BASE}/summary`), enabled })
}

export function useTemplates(type: VehicleType, enabled = true) {
  return useQuery({
    queryKey: ['vehicles', 'templates', type],
    queryFn: () => api<Template[]>(`${BASE}/templates`, { query: { type } }),
    staleTime: Infinity,
    enabled,
  })
}

export function useReadings(id: string) {
  return useQuery({ queryKey: ['vehicles', 'detail', id, 'readings'], queryFn: () => api<Reading[]>(`${BASE}/${id}/readings`) })
}

export function useExpenses(id: string, months: number) {
  return useQuery({
    queryKey: ['vehicles', 'detail', id, 'expenses', months],
    queryFn: () => api<Expense[]>(`${BASE}/${id}/expenses`, { query: { months: String(months) } }),
  })
}

export function useVehicleStats(id: string, months: number) {
  return useQuery({
    queryKey: ['vehicles', 'detail', id, 'stats', months],
    queryFn: () => api<VehicleStats>(`${BASE}/${id}/stats`, { query: { months: String(months) } }),
  })
}

export function useServiceLogs(id: string) {
  return useQuery({ queryKey: ['vehicles', 'detail', id, 'logs'], queryFn: () => api<ServiceLog[]>(`${BASE}/${id}/logs`) })
}

export function useSaveVehicle() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, ...body }: VehicleRequest & { id?: string }) =>
      api<VehicleDetail>(id ? `${BASE}/${id}` : BASE, { method: id ? 'PUT' : 'POST', body }),
    onSuccess: invalidate,
  })
}

export function useArchiveVehicle() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, archived }: { id: string; archived: boolean }) =>
      api<VehicleDetail>(`${BASE}/${id}/archive`, { method: 'PUT', body: { archived } }),
    onSuccess: invalidate,
  })
}

export function useDeleteVehicle() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => api<void>(`${BASE}/${id}`, { method: 'DELETE' }), onSuccess: invalidate })
}

export function useVehicleImage() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, image }: { id: string; image: Blob | null }) => {
      if (!image) return api<VehicleDetail>(`${BASE}/${id}/image`, { method: 'DELETE' })
      const form = new FormData()
      form.append('file', image, 'veiculo.jpg')
      return api<VehicleDetail>(`${BASE}/${id}/image`, { method: 'PUT', body: form })
    },
    onSuccess: invalidate,
  })
}

export function useRefreshFipe() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => api<VehicleDetail>(`${BASE}/${id}/fipe/refresh`, { method: 'POST' }), onSuccess: invalidate })
}

export function useAddReading() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, km, date }: { id: string; km: number; date: string }) =>
      api<Reading>(`${BASE}/${id}/readings`, { method: 'POST', body: { km, date } }),
    onSuccess: invalidate,
  })
}

export function useDeleteReading() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, readingId }: { id: string; readingId: string }) =>
      api<void>(`${BASE}/${id}/readings/${readingId}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}

export function useSaveExpense() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, expenseId, ...body }: ExpenseRequest & { id: string; expenseId?: string }) =>
      api<Expense>(expenseId ? `${BASE}/${id}/expenses/${expenseId}` : `${BASE}/${id}/expenses`,
        { method: expenseId ? 'PUT' : 'POST', body }),
    onSuccess: invalidate,
  })
}

export function useDeleteExpense() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, expenseId }: { id: string; expenseId: string }) =>
      api<void>(`${BASE}/${id}/expenses/${expenseId}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}

export function useExpenseImage() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, expenseId, image }: { id: string; expenseId: string; image: Blob | null }) => {
      const url = `${BASE}/${id}/expenses/${expenseId}/image`
      if (!image) return api<Expense>(url, { method: 'DELETE' })
      const form = new FormData()
      form.append('file', image, 'comprovante.jpg')
      return api<Expense>(url, { method: 'PUT', body: form })
    },
    onSuccess: invalidate,
  })
}

/** Itens, revisões e documentos: todas as rotas devolvem o detalhe do veículo. */
export function useCare() {
  const invalidate = useInvalidate()
  const call = (path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown) =>
    api<VehicleDetail>(`${BASE}/${path}`, { method, body })
  return useMutation({
    mutationFn: (action: CareAction) => {
      const v = action.vehicleId
      switch (action.kind) {
        case 'saveItem': return call(action.itemId ? `${v}/items/${action.itemId}` : `${v}/items`, action.itemId ? 'PUT' : 'POST', action.body)
        case 'deleteItem': return call(`${v}/items/${action.itemId}`, 'DELETE')
        case 'itemDone': return call(`${v}/items/${action.itemId}/done`, 'POST', action.body)
        case 'saveRevision':
          return call(action.revisionId ? `${v}/revisions/${action.revisionId}` : `${v}/revisions`, action.revisionId ? 'PUT' : 'POST', action.body)
        case 'deleteRevision': return call(`${v}/revisions/${action.revisionId}`, 'DELETE')
        case 'plan': return call(`${v}/revisions/plan`, 'POST', action.body)
        case 'revisionDone': return call(`${v}/revisions/${action.revisionId}/done`, 'POST', action.body)
        case 'reopenRevision': return call(`${v}/revisions/${action.revisionId}/done`, 'DELETE')
        case 'saveDocument':
          return call(action.documentId ? `${v}/documents/${action.documentId}` : `${v}/documents`, action.documentId ? 'PUT' : 'POST', action.body)
        case 'deleteDocument': return call(`${v}/documents/${action.documentId}`, 'DELETE')
        case 'renew': return call(`${v}/documents/${action.documentId}/renew`, 'POST', action.body)
      }
    },
    onSuccess: invalidate,
  })
}

export type CareAction = { vehicleId: string } & (
  | { kind: 'saveItem'; itemId?: string; body: ItemRequest }
  | { kind: 'deleteItem'; itemId: string }
  | { kind: 'itemDone'; itemId: string; body: DoneRequest }
  | { kind: 'saveRevision'; revisionId?: string; body: RevisionRequest }
  | { kind: 'deleteRevision'; revisionId: string }
  | { kind: 'plan'; body: PlanRequest }
  | { kind: 'revisionDone'; revisionId: string; body: DoneRequest }
  | { kind: 'reopenRevision'; revisionId: string }
  | { kind: 'saveDocument'; documentId?: string; body: DocumentRequest }
  | { kind: 'deleteDocument'; documentId: string }
  | { kind: 'renew'; documentId: string; body: RenewRequest }
)

export function useShares(id: string, enabled: boolean) {
  return useQuery({ queryKey: ['vehicles', 'detail', id, 'shares'], queryFn: () => api<Shares>(`${BASE}/${id}/shares`), enabled })
}

export function useShareOptions(id: string, enabled: boolean) {
  return useQuery({
    queryKey: ['vehicles', 'detail', id, 'share-options'],
    queryFn: () => api<ShareOptions>(`${BASE}/${id}/share-options`),
    enabled,
  })
}

export type ShareTarget = { id: string; kind: 'users' | 'groups'; targetId: string }

export function useShare() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, kind, targetId, role }: ShareTarget & { role: Exclude<VehicleRole, 'OWNER'> }) =>
      api<void>(`${BASE}/${id}/shares/${kind}/${targetId}`, { method: 'PUT', body: { role } }),
    onSuccess: invalidate,
  })
}

export function useUnshare() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, kind, targetId }: ShareTarget) => api<void>(`${BASE}/${id}/shares/${kind}/${targetId}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}

export function useLeaveVehicle() {
  const invalidate = useInvalidate()
  return useMutation({ mutationFn: (id: string) => api<void>(`${BASE}/${id}/shares/me`, { method: 'DELETE' }), onSuccess: invalidate })
}

export function useFipeBrands(kind: FipeKind | null) {
  return useQuery({
    queryKey: ['vehicles', 'fipe', kind],
    queryFn: () => api<FipeOption[]>(`${BASE}/fipe/${kind}/brands`),
    enabled: !!kind,
    staleTime: Infinity,
    retry: false,
  })
}

export function useFipeModels(kind: FipeKind | null, brand: string | null) {
  return useQuery({
    queryKey: ['vehicles', 'fipe', kind, brand],
    queryFn: () => api<FipeOption[]>(`${BASE}/fipe/${kind}/brands/${brand}/models`),
    enabled: !!kind && !!brand,
    staleTime: Infinity,
    retry: false,
  })
}

export function useFipeYears(kind: FipeKind | null, brand: string | null, model: string | null) {
  return useQuery({
    queryKey: ['vehicles', 'fipe', kind, brand, model],
    queryFn: () => api<FipeOption[]>(`${BASE}/fipe/${kind}/brands/${brand}/models/${model}/years`),
    enabled: !!kind && !!brand && !!model,
    staleTime: Infinity,
    retry: false,
  })
}

export function useFipePrice(kind: FipeKind | null, brand: string | null, model: string | null, year: string | null) {
  return useQuery({
    queryKey: ['vehicles', 'fipe', kind, brand, model, year],
    queryFn: () => api<FipePrice>(`${BASE}/fipe/${kind}/brands/${brand}/models/${model}/years/${year}`),
    enabled: !!kind && !!brand && !!model && !!year,
    staleTime: 60 * 60_000,
    retry: false,
  })
}
