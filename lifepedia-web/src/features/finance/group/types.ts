import type { YearMonth } from '@/lib/format'
import type { UserSummary } from '@/features/groups/types'
import type { EntryType, MonthlySummary } from '../types'

/** SYNC: acompanha o item pessoal. COPY: cópia independente, editável no grupo por quem levou. */
export type ShareMode = 'SYNC' | 'COPY'

export type CategoryRef = { id: string; name: string; color: string }

type GroupItemBase = {
  id: string
  description: string
  type: EntryType
  amount: number
  notes: string | null
  /** Categoria de quem levou */
  category: CategoryRef
  member: UserSummary
  synced: boolean
  /** Levado pelo usuário atual */
  mine: boolean
}

export type GroupTransaction = GroupItemBase & { date: string; month: YearMonth }

export type GroupRecurringEntry = GroupItemBase & {
  dayOfMonth: number | null
  startMonth: YearMonth
  endMonth: YearMonth | null
  active: boolean
}

export type MemberTotals = { member: UserSummary; income: number; expense: number; invested: number; balance: number }

/** Reserva até o mês: valor do planejamento + investimentos lançados no grupo. */
export type GroupReserve = { manual: number; invested: number; total: number }

/** Mesmo formato do resumo pessoal (categorias somadas pelo nome) + total por membro. */
export type GroupFinanceSummary = MonthlySummary & { members: MemberTotals[]; reserve: GroupReserve }

export type ShareRequest = { transactionIds?: string[]; recurringIds?: string[]; mode: ShareMode }

export type Origin = { originId: string; synced: boolean }
export type Origins = { transactions: Origin[]; recurring: Origin[] }
