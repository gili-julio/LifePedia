import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { CompareItem, SavedFinanceSimulation, SimulationInput, SimulationResult } from './types'

const BASE = '/api/finance/simulator'

/** Refeita a cada mudança do formulário; nada é salvo. */
export function useFinanceSimulation(input: SimulationInput | null, real: boolean) {
  return useQuery({
    queryKey: ['finance', 'simulator', 'run', input, real],
    queryFn: () => api<SimulationResult>(`${BASE}/run`, { method: 'POST', body: input, query: { real: real ? 'true' : undefined } }),
    enabled: !!input,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    retry: false,
  })
}

export function useCompareSimulations(ids: string[], real: boolean) {
  return useQuery({
    queryKey: ['finance', 'simulator', 'compare', ids, real],
    queryFn: () => api<CompareItem[]>(`${BASE}/compare`, { method: 'POST', body: { ids }, query: { real: real ? 'true' : undefined } }),
    enabled: ids.length >= 2,
    placeholderData: keepPreviousData,
    retry: false,
  })
}

export function useSavedFinanceSimulations() {
  return useQuery({
    queryKey: ['finance', 'simulator', 'saved'],
    queryFn: () => api<SavedFinanceSimulation[]>(`${BASE}/saved`),
  })
}

export function useSaveFinanceSimulation() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, name, input }: { id?: string; name: string; input: SimulationInput }) =>
      api<SavedFinanceSimulation>(id ? `${BASE}/saved/${id}` : `${BASE}/saved`, { method: id ? 'PUT' : 'POST', body: { name, input } }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['finance', 'simulator'] }),
  })
}

export function useDeleteFinanceSimulation() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`${BASE}/saved/${id}`, { method: 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['finance', 'simulator'] }),
  })
}
