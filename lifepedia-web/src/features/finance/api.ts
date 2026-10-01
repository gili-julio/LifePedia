import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import type { Page } from '@/components/data-table/list-state'
import { api } from '@/lib/api'
import type { YearMonth } from '@/lib/format'
import type {
  Category,
  CategoryItem,
  CategoryRequest,
  EntryType,
  FixedAdjustment,
  ImportRequest,
  ImportResponse,
  MonthlySummary,
  MonthPoint,
  RecurringEntry,
  RecurringEntryRequest,
  Transaction,
  TransactionRequest,
} from './types'

/** Parâmetros de listagem paginada (`sort` = `campo,asc|desc`). */
export type PageParams = { page: number; size: number; sort: string }

/** Chaves sob 'finance': toda mutação invalida o módulo inteiro. */
const keys = {
  all: ['finance'] as const,
  summary: (month: YearMonth) => ['finance', 'summary', month] as const,
  history: (until: YearMonth, months: number) => ['finance', 'history', until, months] as const,
  categoryItems: (month: YearMonth, categoryId: string) => ['finance', 'summary', month, 'category', categoryId] as const,
  categories: ['finance', 'categories'] as const,
  recurring: ['finance', 'recurring'] as const,
  recurringPage: (type: EntryType, params: PageParams) => ['finance', 'recurring', 'page', type, params] as const,
  transactions: (month: YearMonth) => ['finance', 'transactions', month] as const,
  transactionsPage: (month: YearMonth, type: EntryType | undefined, params: PageParams) =>
    ['finance', 'transactions', 'page', month, type, params] as const,
  transactionsSearch: (filter: TransactionSearch) => ['finance', 'transactions', 'search', filter] as const,
  descriptions: ['finance', 'transactions', 'descriptions'] as const,
}

/** Filtro de `/transactions/all` (descrição sem diferenciar maiúsculas). */
export type TransactionSearch = { from: YearMonth; to?: YearMonth; type?: EntryType; description?: string }

export function useSummary(month: YearMonth) {
  return useQuery({
    queryKey: keys.summary(month),
    queryFn: () => api<MonthlySummary>('/api/finance/summary', { query: { month } }),
    placeholderData: keepPreviousData,
  })
}

export function useHistory(until: YearMonth, months = 6) {
  return useQuery({
    queryKey: keys.history(until, months),
    queryFn: () => api<MonthPoint[]>('/api/finance/summary/history', { query: { until, months } }),
    placeholderData: keepPreviousData,
  })
}

export function useCategoryItems(month: YearMonth, categoryId: string, enabled: boolean) {
  return useQuery({
    queryKey: keys.categoryItems(month, categoryId),
    queryFn: () => api<CategoryItem[]>(`/api/finance/summary/categories/${categoryId}`, { query: { month } }),
    enabled,
  })
}

/** Todas as categorias (seleção, prompt). */
export function useCategories() {
  return useQuery({
    queryKey: keys.categories,
    queryFn: () => api<Category[]>('/api/finance/categories/all'),
    staleTime: 5 * 60_000,
  })
}

/** Todos os fixos (importação, ajustes). */
export function useRecurringEntries() {
  return useQuery({
    queryKey: keys.recurring,
    queryFn: () => api<RecurringEntry[]>('/api/finance/recurring/all'),
  })
}

export function useRecurringPage(type: EntryType, params: PageParams) {
  return useQuery({
    queryKey: keys.recurringPage(type, params),
    queryFn: () => api<Page<RecurringEntry>>('/api/finance/recurring', { query: { type, ...params } }),
    placeholderData: keepPreviousData,
  })
}

/** Todos os ocasionais do mês fora de componente (checagem de duplicados). */
export function fetchTransactions(queryClient: QueryClient, month: YearMonth) {
  return queryClient.fetchQuery({
    queryKey: keys.transactions(month),
    queryFn: () => api<Transaction[]>('/api/finance/transactions/all', { query: { month } }),
  })
}

export function useTransactionsPage(month: YearMonth, type: EntryType | undefined, params: PageParams) {
  return useQuery({
    queryKey: keys.transactionsPage(month, type, params),
    queryFn: () => api<Page<Transaction>>('/api/finance/transactions', { query: { month, type, ...params } }),
    placeholderData: keepPreviousData,
  })
}

export function useTransactionsSearch(filter: TransactionSearch, enabled: boolean) {
  return useQuery({
    queryKey: keys.transactionsSearch(filter),
    queryFn: () => api<Transaction[]>('/api/finance/transactions/all', { query: filter }),
    enabled,
  })
}

/** Nomes mais usados nos últimos 12 meses (padronização da importação). */
export function useTransactionDescriptions() {
  return useQuery({
    queryKey: keys.descriptions,
    queryFn: () => api<string[]>('/api/finance/transactions/descriptions'),
  })
}

function useFinanceMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  })
}

export function useSaveRecurringEntry() {
  return useFinanceMutation(({ id, data }: { id?: string; data: RecurringEntryRequest }) =>
    id
      ? api<RecurringEntry>(`/api/finance/recurring/${id}`, { method: 'PUT', body: data })
      : api<RecurringEntry>('/api/finance/recurring', { method: 'POST', body: data }),
  )
}

export function useDeleteRecurringEntry() {
  return useFinanceMutation((id: string) => api<void>(`/api/finance/recurring/${id}`, { method: 'DELETE' }))
}

export function useSaveTransaction() {
  return useFinanceMutation(({ id, data }: { id?: string; data: TransactionRequest }) =>
    id
      ? api<Transaction>(`/api/finance/transactions/${id}`, { method: 'PUT', body: data })
      : api<Transaction>('/api/finance/transactions', { method: 'POST', body: data }),
  )
}

export function useDeleteTransaction() {
  return useFinanceMutation((id: string) => api<void>(`/api/finance/transactions/${id}`, { method: 'DELETE' }))
}

export function useSaveCategory() {
  return useFinanceMutation(({ id, data }: { id?: string; data: CategoryRequest }) =>
    id
      ? api<Category>(`/api/finance/categories/${id}`, { method: 'PUT', body: data })
      : api<Category>('/api/finance/categories', { method: 'POST', body: data }),
  )
}

/** `replaceWith` recebe os lançamentos, se a categoria estiver em uso. */
export function useDeleteCategory() {
  return useFinanceMutation(({ id, replaceWith }: { id: string; replaceWith?: string }) =>
    api<void>(`/api/finance/categories/${id}`, { method: 'DELETE', query: { replaceWith } }),
  )
}

/** Ocasionais + valor real de fixos no mês, tudo ou nada. */
export function useImport() {
  return useFinanceMutation((request: ImportRequest) =>
    api<ImportResponse>('/api/finance/import', { method: 'POST', body: request }),
  )
}

/** Valor real do fixo no mês. */
export function useSaveAdjustment() {
  return useFinanceMutation(({ id, month, amount }: { id: string; month: YearMonth; amount: number }) =>
    api<FixedAdjustment>(`/api/finance/recurring/${id}/adjustments/${month}`, { method: 'PUT', body: { amount } }),
  )
}

export function useRemoveAdjustment() {
  return useFinanceMutation(({ id, month }: { id: string; month: YearMonth }) =>
    api<void>(`/api/finance/recurring/${id}/adjustments/${month}`, { method: 'DELETE' }),
  )
}
