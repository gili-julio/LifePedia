import { keepPreviousData, useIsMutating, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import type { Page } from '@/components/data-table/list-state'
import { api, ApiError } from '@/lib/api'
import type { BankRef } from '@/lib/banks'
import type { YearMonth } from '@/lib/format'
import type {
  BankFilter,
  BankOverview,
  Category,
  CategoryItem,
  CategoryRequest,
  EntryType,
  FixedAdjustment,
  ImportAssistant,
  ImportDraft,
  ImportDraftContent,
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

const DRAFT_POLL_MS = 4000
const SEND_DOCUMENTS = ['finance', 'import', 'send']

const draftBody = (rows: unknown[]) => ({ rows: JSON.stringify(rows), itemCount: rows.length })

/** Chaves sob 'finance': toda mutação invalida o módulo inteiro. */
const keys = {
  all: ['finance'] as const,
  importAssistant: ['finance', 'import', 'assistant'] as const,
  importPrompt: ['finance', 'import', 'prompt'] as const,
  importDraft: ['finance', 'import', 'draft'] as const,
  summary: (month: YearMonth, filter: BankFilter) => ['finance', 'summary', month, filter] as const,
  banks: (month: YearMonth, months: number) => ['finance', 'banks', month, months] as const,
  lastBank: ['finance', 'banks', 'last'] as const,
  history: (until: YearMonth, months: number) => ['finance', 'history', until, months] as const,
  categoryItems: (month: YearMonth, categoryId: string) => ['finance', 'summary', month, 'category', categoryId] as const,
  categories: ['finance', 'categories'] as const,
  recurring: ['finance', 'recurring'] as const,
  recurringPage: (type: EntryType, params: PageParams, filter: BankFilter) => ['finance', 'recurring', 'page', type, params, filter] as const,
  transactions: (month: YearMonth) => ['finance', 'transactions', month] as const,
  transactionsPage: (month: YearMonth, type: EntryType | undefined, params: PageParams, filter: BankFilter) =>
    ['finance', 'transactions', 'page', month, type, params, filter] as const,
  transactionsSearch: (filter: TransactionSearch) => ['finance', 'transactions', 'search', filter] as const,
  descriptions: ['finance', 'transactions', 'descriptions'] as const,
}

/** Filtro de `/transactions/all` (descrição sem diferenciar maiúsculas). */
export type TransactionSearch = { from: YearMonth; to?: YearMonth; type?: EntryType; description?: string }

export function useSummary(month: YearMonth, filter: BankFilter = {}) {
  return useQuery({
    queryKey: keys.summary(month, filter),
    queryFn: () => api<MonthlySummary>('/api/finance/summary', { query: { month, ...filter } }),
    placeholderData: keepPreviousData,
  })
}

/** Gastos por banco no mês e nos `months` meses até ele. */
export function useBankOverview(month: YearMonth, months = 6) {
  return useQuery({
    queryKey: keys.banks(month, months),
    queryFn: () => api<BankOverview>('/api/finance/summary/banks', { query: { month, months } }),
    placeholderData: keepPreviousData,
  })
}

/** Último banco usado, para o formulário já abrir com ele. */
export function useLastBank() {
  return useQuery({
    queryKey: keys.lastBank,
    queryFn: () => api<{ bank: BankRef | null }>('/api/finance/banks/last'),
    select: (data) => data.bank,
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

export function useRecurringPage(type: EntryType, params: PageParams, filter: BankFilter = {}) {
  return useQuery({
    queryKey: keys.recurringPage(type, params, filter),
    queryFn: () => api<Page<RecurringEntry>>('/api/finance/recurring', { query: { type, ...params, ...filter } }),
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

export function useTransactionsPage(month: YearMonth, type: EntryType | undefined, params: PageParams, filter: BankFilter = {}) {
  return useQuery({
    queryKey: keys.transactionsPage(month, type, params, filter),
    queryFn: () => api<Page<Transaction>>('/api/finance/transactions', { query: { month, type, ...params, ...filter } }),
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

/** Adianta/adia a parcela; `following` leva as próximas parcelas da mesma compra junto. */
export function useMoveTransactionMonth() {
  return useFinanceMutation(({ id, month, following }: { id: string; month: YearMonth; following: boolean }) =>
    api<{ moved: number }>(`/api/finance/transactions/${id}/month`, { method: 'PUT', body: { month, following } }),
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

export function useImportAssistant() {
  return useQuery({
    queryKey: keys.importAssistant,
    queryFn: () => api<ImportAssistant>('/api/finance/import/assistant'),
  })
}

export function useImportPrompt(enabled: boolean) {
  return useQuery({
    queryKey: keys.importPrompt,
    queryFn: () => api<{ prompt: string }>('/api/finance/import/prompt'),
    enabled,
  })
}

/** Revisão em aberto; enquanto a IA lê, confere a cada poucos segundos. */
export function useImportDraft() {
  return useQuery({
    queryKey: keys.importDraft,
    queryFn: async () => (await api<ImportDraft | undefined>('/api/finance/import/draft')) ?? null,
    refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? DRAFT_POLL_MS : false),
  })
}

export function fetchDraftContent(id: string) {
  return api<ImportDraftContent>(`/api/finance/import/draft/${id}/content`)
}

/** Grava a prévia; fora do `useFinanceMutation` para não recarregar o módulo a cada edição. */
export function saveDraftRows(id: string, rows: unknown[]) {
  return api<ImportDraft>(`/api/finance/import/draft/${id}`, { method: 'PUT', body: draftBody(rows) })
}

function useDraftMutation<TVariables>(fn: (variables: TVariables) => Promise<ImportDraft | undefined>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (draft) => {
      queryClient.setQueryData(keys.importDraft, draft ?? null)
      return queryClient.invalidateQueries({ queryKey: keys.importAssistant })
    },
  })
}

export function useCreateDraft() {
  return useDraftMutation((rows: unknown[]) =>
    api<ImportDraft>('/api/finance/import/draft', { method: 'POST', body: draftBody(rows) }),
  )
}

export function useDiscardDraft() {
  return useDraftMutation((id: string) => api<undefined>(`/api/finance/import/draft/${id}`, { method: 'DELETE' }))
}

/** Avisos aqui, e não na tela: a pessoa pode sair durante o envio. */
export function useSendDocuments() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationKey: SEND_DOCUMENTS,
    mutationFn: (files: File[]) => {
      const body = new FormData()
      for (const file of files) body.append('files', file)
      return api<ImportDraft>('/api/finance/import/analyze', { method: 'POST', body })
    },
    onSuccess: (draft) => {
      queryClient.setQueryData(keys.importDraft, draft)
      toast.success('Leitura iniciada. Pode sair desta tela: avisamos quando terminar.')
      return queryClient.invalidateQueries({ queryKey: keys.importAssistant })
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível enviar os documentos.')
      return queryClient.invalidateQueries({ queryKey: keys.importAssistant })
    },
  })
}

/** Envio em andamento, mesmo depois de sair e voltar para a tela. */
export function useSendingDocuments() {
  return useIsMutating({ mutationKey: SEND_DOCUMENTS }) > 0
}

/** Define (ou tira, com `bank` nulo) o banco de vários itens pessoais. */
export function useAssignBank() {
  return useFinanceMutation((data: { transactionIds?: string[]; recurringIds?: string[]; bank: BankRef | null }) =>
    api<{ updated: number }>('/api/finance/banks/assign', { method: 'PUT', body: data }),
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
