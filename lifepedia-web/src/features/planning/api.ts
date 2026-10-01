import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type {
  AcquireRequest,
  LinkPreview,
  PlanItem,
  PlanItemRequest,
  PlanSettings,
  Reference,
  Scenario,
  ScenarioComparison,
  Simulation,
  WhatIf,
} from './types'

const keys = {
  group: (groupId: string) => ['planning', groupId] as const,
  simulation: (groupId: string, scenarioId: string | null, whatIf: WhatIf) =>
    ['planning', groupId, 'simulation', scenarioId, whatIf] as const,
  scenarios: (groupId: string) => ['planning', groupId, 'scenarios'] as const,
  compare: (groupId: string, whatIf: WhatIf) => ['planning', groupId, 'compare', whatIf] as const,
}

const base = (groupId: string) => `/api/groups/${groupId}/planning`

const whatIfQuery = (w: WhatIf) => ({ extraIncome: w.extraIncome || undefined, extraExpense: w.extraExpense || undefined })

/** Sempre refaz ao abrir: a base depende dos itens financeiros do grupo, que mudam em outras telas. */
export function useSimulation(groupId: string, scenarioId: string | null, whatIf: WhatIf) {
  return useQuery({
    queryKey: keys.simulation(groupId, scenarioId, whatIf),
    queryFn: () => api<Simulation>(base(groupId), { query: { scenario: scenarioId, ...whatIfQuery(whatIf) } }),
    placeholderData: keepPreviousData,
    staleTime: 0,
  })
}

export function useScenarios(groupId: string) {
  return useQuery({ queryKey: keys.scenarios(groupId), queryFn: () => api<Scenario[]>(`${base(groupId)}/scenarios`) })
}

export function useScenarioComparison(groupId: string, whatIf: WhatIf, enabled: boolean) {
  return useQuery({
    queryKey: keys.compare(groupId, whatIf),
    queryFn: () => api<ScenarioComparison[]>(`${base(groupId)}/scenarios/compare`, { query: whatIfQuery(whatIf) }),
    placeholderData: keepPreviousData,
    staleTime: 0,
    enabled,
  })
}

/** Lê nome, preço e foto do link (a API busca). */
export function useLinkPreview() {
  return useMutation({
    mutationFn: ({ groupId, url }: { groupId: string; url: string }) =>
      api<LinkPreview>(`${base(groupId)}/link-preview`, { method: 'POST', body: { url } }),
  })
}

/** Preço e foto de produto parecido com o nome (a API busca no Buscapé); nulo se nada parecido. */
export function usePriceReference() {
  return useMutation({
    mutationFn: ({ groupId, name }: { groupId: string; name: string }) =>
      api<{ reference: Reference | null }>(`${base(groupId)}/price-reference`, { method: 'POST', body: { name } })
        .then((r) => r.reference),
  })
}

/** Toda mutação atualiza o planejamento do grupo (e o financeiro, se lançou uma compra). */
function usePlanningMutation<TVariables extends { groupId: string }, TResult>(
  fn: (variables: TVariables) => Promise<TResult>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (_result, { groupId }) => {
      void queryClient.invalidateQueries({ queryKey: keys.group(groupId) })
      void queryClient.invalidateQueries({ queryKey: ['finance', 'groups', groupId] })
    },
  })
}

export function useSavePlanItem() {
  return usePlanningMutation(({ groupId, id, data }: { groupId: string; id?: string; data: PlanItemRequest }) =>
    id
      ? api<PlanItem>(`${base(groupId)}/items/${id}`, { method: 'PUT', body: data })
      : api<PlanItem>(`${base(groupId)}/items`, { method: 'POST', body: data }),
  )
}

export function useDeletePlanItem() {
  return usePlanningMutation(({ groupId, id }: { groupId: string; id: string }) =>
    api<void>(`${base(groupId)}/items/${id}`, { method: 'DELETE' }),
  )
}

export function useSetItemActive() {
  return usePlanningMutation(({ groupId, id, active }: { groupId: string; id: string; active: boolean }) =>
    api<void>(`${base(groupId)}/items/${id}/active`, { method: 'PUT', body: { active } }),
  )
}

export function useReorderItems() {
  return usePlanningMutation(({ groupId, itemIds }: { groupId: string; itemIds: string[] }) =>
    api<void>(`${base(groupId)}/items/order`, { method: 'PUT', body: { itemIds } }),
  )
}

export function useAcquireItem() {
  return usePlanningMutation(({ groupId, id, data }: { groupId: string; id: string; data: AcquireRequest }) =>
    api<void>(`${base(groupId)}/items/${id}/acquire`, { method: 'POST', body: data }),
  )
}

export function useUnacquireItem() {
  return usePlanningMutation(({ groupId, id }: { groupId: string; id: string }) =>
    api<void>(`${base(groupId)}/items/${id}/acquire`, { method: 'DELETE' }),
  )
}

export function useSaveSettings() {
  return usePlanningMutation(({ groupId, data }: { groupId: string; data: PlanSettings }) =>
    api<PlanSettings>(`${base(groupId)}/settings`, { method: 'PUT', body: data }),
  )
}

export function useSaveScenario() {
  return usePlanningMutation(({ groupId, id, name, itemIds }: { groupId: string; id?: string; name: string; itemIds?: string[] }) =>
    id
      ? api<Scenario>(`${base(groupId)}/scenarios/${id}`, { method: 'PUT', body: { name, itemIds } })
      : api<Scenario>(`${base(groupId)}/scenarios`, { method: 'POST', body: { name, itemIds } }),
  )
}

export function useDeleteScenario() {
  return usePlanningMutation(({ groupId, id }: { groupId: string; id: string }) =>
    api<void>(`${base(groupId)}/scenarios/${id}`, { method: 'DELETE' }),
  )
}

export function useApplyScenario() {
  return usePlanningMutation(({ groupId, id }: { groupId: string; id: string }) =>
    api<void>(`${base(groupId)}/scenarios/${id}/apply`, { method: 'POST' }),
  )
}
