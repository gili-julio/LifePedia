import type { AppModule } from '@/features/auth/types'
import type { EntryType } from '@/features/finance/types'

/** Espelha `SearchKind` da API. */
export type SearchKind =
  | 'TRANSACTION' | 'RECURRING' | 'CATEGORY' | 'CARD' | 'ACCOUNT' | 'GROUP_TRANSACTION' | 'GROUP_RECURRING'
  | 'FINANCING' | 'TASK' | 'EVENT' | 'COMMITMENT' | 'NOTE' | 'DELIVERY' | 'VEHICLE' | 'VEHICLE_EXPENSE'
  | 'VEHICLE_SERVICE' | 'GROUP' | 'PLAN_ITEM' | 'MESSAGE' | 'FRIEND' | 'DEV_PROJECT' | 'DEV_ITEM' | 'ARCH_PROJECT'
  | 'PRICE_WATCH' | 'DEV_REPORT' | 'SCHOOL_COURSE'

export type SearchHit = {
  kind: SearchKind
  id: string
  title: string
  subtitle: string | null
  /** Trecho do texto longo onde achou (nota, mensagem). */
  snippet: string | null
  link: string
  date: string | null
  amount: number | null
  entryType: EntryType | null
  groupName: string | null
  fields: { label: string; value: string }[]
  score: number
}

export type SearchGroup = { kind: SearchKind; module: AppModule; items: SearchHit[]; more: boolean }

export type SearchTotals = { count: number; income: number; expense: number }

export type SearchResponse = { groups: SearchGroup[]; totals: SearchTotals | null }

export type Period = 'all' | 'month' | '30d' | '3m' | 'year'

/** Filtros da busca geral (o texto pode trazer valor: "> 100", "50,90", "100-200"). */
export type SearchFilters = {
  modules: AppModule[]
  period: Period
  type?: EntryType
  min?: number
  max?: number
  /** Só o que é deste grupo. */
  groupId?: string
  kinds?: SearchKind[]
}
