import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Page } from '@/components/data-table/list-state'
import { api } from '@/lib/api'
import type { YearMonth } from '@/lib/format'
import type { PageParams } from '../api'
import type { CategoryItem, EntryType, MonthPoint, RecurringEntryRequest, TransactionRequest } from '../types'
import type { GroupFinanceSummary, GroupRecurringEntry, GroupTransaction, Origins, ShareRequest } from './types'

/** Sob ['finance', 'groups']: mutações do financeiro pessoal (invalidam 'finance') também atualizam os grupos. */
const keys = {
  all: ['finance', 'groups'] as const,
  group: (groupId: string) => ['finance', 'groups', groupId] as const,
  summary: (groupId: string, month: YearMonth) => ['finance', 'groups', groupId, 'summary', month] as const,
  history: (groupId: string, until: YearMonth, months: number) => ['finance', 'groups', groupId, 'history', until, months] as const,
  categoryItems: (groupId: string, month: YearMonth, category: string) =>
    ['finance', 'groups', groupId, 'summary', month, 'category', category] as const,
  transactions: (groupId: string, month: YearMonth, type: EntryType | undefined, params: PageParams) =>
    ['finance', 'groups', groupId, 'transactions', month, type, params] as const,
  recurring: (groupId: string, type: EntryType, params: PageParams) =>
    ['finance', 'groups', groupId, 'recurring', type, params] as const,
  origins: (groupId: string) => ['finance', 'groups', groupId, 'origins'] as const,
}

const base = (groupId: string) => `/api/groups/${groupId}/finance`

export function useGroupSummary(groupId: string, month: YearMonth) {
  return useQuery({
    queryKey: keys.summary(groupId, month),
    queryFn: () => api<GroupFinanceSummary>(`${base(groupId)}/summary`, { query: { month } }),
    placeholderData: keepPreviousData,
  })
}

export function useGroupHistory(groupId: string, until: YearMonth, months = 6) {
  return useQuery({
    queryKey: keys.history(groupId, until, months),
    queryFn: () => api<MonthPoint[]>(`${base(groupId)}/summary/history`, { query: { until, months } }),
  })
}

export function useGroupCategoryItems(groupId: string, month: YearMonth, category: string) {
  return useQuery({
    queryKey: keys.categoryItems(groupId, month, category),
    queryFn: () => api<CategoryItem[]>(`${base(groupId)}/summary/category-items`, { query: { month, category } }),
  })
}

export function useGroupTransactionsPage(groupId: string, month: YearMonth, type: EntryType | undefined, params: PageParams) {
  return useQuery({
    queryKey: keys.transactions(groupId, month, type, params),
    queryFn: () => api<Page<GroupTransaction>>(`${base(groupId)}/transactions`, { query: { month, type, ...params } }),
    placeholderData: keepPreviousData,
  })
}

export function useGroupRecurringPage(groupId: string, type: EntryType, params: PageParams) {
  return useQuery({
    queryKey: keys.recurring(groupId, type, params),
    queryFn: () => api<Page<GroupRecurringEntry>>(`${base(groupId)}/recurring`, { query: { type, ...params } }),
    placeholderData: keepPreviousData,
  })
}

/** Meus itens pessoais que já estão no grupo. */
export function useGroupOrigins(groupId: string, enabled: boolean) {
  return useQuery({ queryKey: keys.origins(groupId), queryFn: () => api<Origins>(`${base(groupId)}/origins`), enabled })
}

function useGroupFinanceMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  })
}

export function useShareItems() {
  return useGroupFinanceMutation(({ groupId, data }: { groupId: string; data: ShareRequest }) =>
    api<{ added: number; skipped: number }>(`${base(groupId)}/items`, { method: 'POST', body: data }),
  )
}

/** Lançamento só do grupo (sem item pessoal). */
export function useCreateGroupTransaction() {
  return useGroupFinanceMutation(({ groupId, data }: { groupId: string; data: TransactionRequest }) =>
    api<GroupTransaction>(`${base(groupId)}/transactions`, { method: 'POST', body: data }),
  )
}

/** Fixo só do grupo (sem item pessoal). */
export function useCreateGroupRecurring() {
  return useGroupFinanceMutation(({ groupId, data }: { groupId: string; data: RecurringEntryRequest }) =>
    api<GroupRecurringEntry>(`${base(groupId)}/recurring`, { method: 'POST', body: data }),
  )
}

export function useUpdateGroupTransaction() {
  return useGroupFinanceMutation(({ groupId, id, data }: { groupId: string; id: string; data: TransactionRequest }) =>
    api<void>(`${base(groupId)}/transactions/${id}`, { method: 'PUT', body: data }),
  )
}

export function useUpdateGroupRecurring() {
  return useGroupFinanceMutation(({ groupId, id, data }: { groupId: string; id: string; data: RecurringEntryRequest }) =>
    api<void>(`${base(groupId)}/recurring/${id}`, { method: 'PUT', body: data }),
  )
}

export function useRemoveGroupItem() {
  return useGroupFinanceMutation(({ groupId, id, kind }: { groupId: string; id: string; kind: 'transactions' | 'recurring' }) =>
    api<void>(`${base(groupId)}/${kind}/${id}`, { method: 'DELETE' }),
  )
}
