import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { YearMonth } from '@/lib/format'
import type {
  AccountRequest,
  AccountView,
  CardDetail,
  CardRequest,
  GroupCard,
  PurchaseRequest,
  PurchaseResult,
  RecalculateItem,
  Wallet,
} from './types'

const BASE = '/api/finance/wallet'

/** Sob 'finance': qualquer mutação do financeiro recarrega a carteira (lançamento muda a fatura). */
const keys = {
  wallet: ['finance', 'wallet'] as const,
  card: (id: string, month: YearMonth | null) => ['finance', 'wallet', 'card', id, month] as const,
  recalculate: (id: string, from: YearMonth) => ['finance', 'wallet', 'card', id, 'recalculate', from] as const,
  group: (groupId: string) => ['finance', 'groups', groupId, 'cards'] as const,
}

export function useWallet() {
  return useQuery({ queryKey: keys.wallet, queryFn: () => api<Wallet>(BASE) })
}

/** `month` nulo = fatura aberta. */
export function useCard(id: string, month: YearMonth | null) {
  return useQuery({
    queryKey: keys.card(id, month),
    queryFn: () => api<CardDetail>(`${BASE}/cards/${id}`, { query: { month } }),
    placeholderData: keepPreviousData,
  })
}

export function useRecalculatePreview(id: string, from: YearMonth, enabled: boolean) {
  return useQuery({
    queryKey: keys.recalculate(id, from),
    queryFn: () => api<RecalculateItem[]>(`${BASE}/cards/${id}/recalculate`, { query: { from } }),
    enabled,
  })
}

export function useGroupCards(groupId: string) {
  return useQuery({ queryKey: keys.group(groupId), queryFn: () => api<GroupCard[]>(`/api/groups/${groupId}/finance/cards`) })
}

function useWalletMutation<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['finance'] }),
  })
}

export function useSaveCard() {
  return useWalletMutation(({ id, data }: { id?: string; data: CardRequest }) =>
    id
      ? api<CardDetail>(`${BASE}/cards/${id}`, { method: 'PUT', body: data })
      : api<CardDetail>(`${BASE}/cards`, { method: 'POST', body: data }),
  )
}

export function useDeleteCard() {
  return useWalletMutation((id: string) => api<void>(`${BASE}/cards/${id}`, { method: 'DELETE' }))
}

export function useSaveAccount() {
  return useWalletMutation(({ id, data }: { id?: string; data: AccountRequest }) =>
    id
      ? api<AccountView>(`${BASE}/accounts/${id}`, { method: 'PUT', body: data })
      : api<AccountView>(`${BASE}/accounts`, { method: 'POST', body: data }),
  )
}

export function useSetBalance() {
  return useWalletMutation(({ id, balance, balanceOn }: { id: string; balance: number; balanceOn: string }) =>
    api<AccountView>(`${BASE}/accounts/${id}/balance`, { method: 'PUT', body: { balance, balanceOn } }),
  )
}

export function useDeleteAccount() {
  return useWalletMutation((id: string) => api<void>(`${BASE}/accounts/${id}`, { method: 'DELETE' }))
}

/** `paid` false desfaz o pagamento. */
export function usePayInvoice() {
  return useWalletMutation(({ cardId, month, paid, paidOn, amount, accountId }: {
    cardId: string
    month: YearMonth
    paid: boolean
    paidOn?: string
    amount?: number
    accountId?: string | null
  }) =>
    api<CardDetail>(`${BASE}/cards/${cardId}/invoices/${month}/paid`, paid
      ? { method: 'PUT', body: { paidOn, amount, accountId: accountId ?? null } }
      : { method: 'DELETE' }),
  )
}

export function useRecalculate() {
  return useWalletMutation(({ cardId, from, ids }: { cardId: string; from: YearMonth; ids: string[] }) =>
    api<{ moved: number }>(`${BASE}/cards/${cardId}/recalculate`, { method: 'POST', body: { from, ids } }),
  )
}

export function usePurchase() {
  return useWalletMutation((data: PurchaseRequest) => api<PurchaseResult>(`${BASE}/purchases`, { method: 'POST', body: data }))
}
