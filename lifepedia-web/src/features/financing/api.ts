import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  CostRequest,
  ExtraPreview,
  ExtraRequest,
  Financing,
  FinancingKind,
  FinancingRequest,
  FinancingSummary,
  RateSuggestion,
  SavedSimulation,
  SimulationRequest,
  SimulationResponse,
} from './types'

/** Rotas dos financiamentos pessoais (`groupId` nulo) ou de um grupo. */
export const financingBase = (groupId: string | null) => (groupId ? `/api/groups/${groupId}/financings` : '/api/financings')
/** Chaves: `['financing', 'me' | groupId, ...]`. */
const scopeKey = (groupId: string | null) => groupId ?? 'me'

/** Tela do financiamento (pessoal ou do grupo). */
export const financingUrl = (groupId: string | null, id: string) =>
  groupId ? `/grupos/${groupId}/financiamentos/${id}` : `/financiamentos/${id}`

/** Simulador; no grupo salva as simulações no grupo. */
export const simulatorUrl = (groupId: string | null, params?: Record<string, string>) => {
  const query = new URLSearchParams({ ...(groupId ? { grupo: groupId } : {}), ...params }).toString()
  return `/financiamentos/simular${query ? `?${query}` : ''}`
}

/** Refeita a cada mudança do formulário; nada é salvo. */
export function useSimulation(groupId: string | null, request: SimulationRequest | null) {
  return useQuery({
    queryKey: ['financing', scopeKey(groupId), 'simulate', request],
    queryFn: () => api<SimulationResponse>(`${financingBase(groupId)}/simulate`, { method: 'POST', body: request }),
    enabled: !!request,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    retry: false,
  })
}

export function useRateSuggestion(groupId: string | null, kind: FinancingKind, enabled = true) {
  return useQuery({
    queryKey: ['financing', scopeKey(groupId), 'rates', kind],
    queryFn: () => api<RateSuggestion>(`${financingBase(groupId)}/rates`, { query: { kind } }),
    enabled,
    staleTime: 10 * 60_000,
  })
}

export function useSavedSimulations(groupId: string | null) {
  return useQuery({
    queryKey: ['financing', scopeKey(groupId), 'simulations'],
    queryFn: () => api<SavedSimulation[]>(`${financingBase(groupId)}/simulations`),
  })
}

export function useFinancings(groupId: string | null) {
  return useQuery({
    queryKey: ['financing', scopeKey(groupId), 'list'],
    queryFn: () => api<FinancingSummary[]>(financingBase(groupId)),
  })
}

/** Pessoais e de grupos de que a pessoa participa. */
export function useFinancingOverview() {
  return useQuery({ queryKey: ['financing', 'overview'], queryFn: () => api<FinancingSummary[]>('/api/financings/overview') })
}

export function useFinancing(groupId: string | null, id: string) {
  return useQuery({
    queryKey: ['financing', scopeKey(groupId), 'detail', id],
    queryFn: () => api<Financing>(`${financingBase(groupId)}/${id}`),
  })
}

export function previewExtra(groupId: string | null, id: string, data: ExtraRequest) {
  return api<ExtraPreview>(`${financingBase(groupId)}/${id}/extras/preview`, { method: 'POST', body: data })
}

/** Toda mutação invalida o módulo; o fixo gerado mexe também no financeiro. */
function useFinancingMutation<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: ['financing'] }),
      queryClient.invalidateQueries({ queryKey: ['finance'] }),
    ]),
  })
}

export function useSaveSimulation(groupId: string | null) {
  return useFinancingMutation((data: { name: string; input: SimulationRequest }) =>
    api<SavedSimulation>(`${financingBase(groupId)}/simulations`, { method: 'POST', body: data }))
}

export function useDeleteSimulation(groupId: string | null) {
  return useFinancingMutation((id: string) => api<void>(`${financingBase(groupId)}/simulations/${id}`, { method: 'DELETE' }))
}

export function useSaveFinancing(groupId: string | null) {
  return useFinancingMutation(({ id, data }: { id?: string; data: FinancingRequest }) =>
    api<Financing>(id ? `${financingBase(groupId)}/${id}` : financingBase(groupId), { method: id ? 'PUT' : 'POST', body: data }))
}

export function useDeleteFinancing(groupId: string | null) {
  return useFinancingMutation((id: string) => api<void>(`${financingBase(groupId)}/${id}`, { method: 'DELETE' }))
}

export function useAddExtra(groupId: string | null, id: string) {
  return useFinancingMutation((data: ExtraRequest) =>
    api<Financing>(`${financingBase(groupId)}/${id}/extras`, { method: 'POST', body: data }))
}

export function useRemoveExtra(groupId: string | null, id: string) {
  return useFinancingMutation((extraId: string) =>
    api<Financing>(`${financingBase(groupId)}/${id}/extras/${extraId}`, { method: 'DELETE' }))
}

export function useSaveCost(groupId: string | null, id: string) {
  return useFinancingMutation(({ costId, data }: { costId?: string; data: CostRequest }) =>
    api<Financing>(`${financingBase(groupId)}/${id}/costs${costId ? `/${costId}` : ''}`, { method: costId ? 'PUT' : 'POST', body: data }))
}

export function useRemoveCost(groupId: string | null, id: string) {
  return useFinancingMutation((costId: string) =>
    api<Financing>(`${financingBase(groupId)}/${id}/costs/${costId}`, { method: 'DELETE' }))
}

export function useMyFixed(groupId: string | null, id: string) {
  return useFinancingMutation((data: { enabled: boolean; categoryId?: string | null; shareToGroup?: boolean }) =>
    api<Financing>(`${financingBase(groupId)}/${id}/my-fixed`, { method: 'PUT', body: data }))
}
