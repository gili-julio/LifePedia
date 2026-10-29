import type { YearMonth } from '@/lib/format'

export type EntryType = 'INCOME' | 'EXPENSE'

/** Categoria do usuário. */
export type Category = {
  id: string
  name: string
  type: EntryType
  /** Hex #rrggbb */
  color: string
  /** Lançamentos na categoria */
  usageCount: number
}

export type CategoryRequest = Pick<Category, 'name' | 'type' | 'color'>

/** Lançamento fixo mensal. */
export type RecurringEntry = {
  id: string
  description: string
  type: EntryType
  categoryId: string
  amount: number
  dayOfMonth: number | null
  startMonth: YearMonth
  endMonth: YearMonth | null
  active: boolean
  notes: string | null
  /** Valor real em meses específicos */
  adjustments: FixedAdjustment[]
}

export type FixedAdjustment = { month: YearMonth; amount: number }

/** Grupos para onde levar o item ao criar (`shareMode` padrão SYNC); ignorado na edição. */
export type ShareOnCreate = { groupIds?: string[]; shareMode?: 'SYNC' | 'COPY' }

/** `removeTransactionIds`: ocasionais que o fixo substitui (só na criação). */
export type RecurringEntryRequest = Omit<RecurringEntry, 'id' | 'adjustments'> & ShareOnCreate & { removeTransactionIds?: string[] }

/** Lançamento ocasional (avulso). */
export type Transaction = {
  id: string
  description: string
  type: EntryType
  categoryId: string
  amount: number
  date: string
  /** Mês em que conta nos totais (ex.: mês da fatura) */
  month: YearMonth
  notes: string | null
  /** Descrição como veio no documento importado (não muda) */
  originalDescription: string | null
}

/** Sem `month`, conta no mês da data. `originalDescription` só vale na criação. */
export type TransactionRequest = Omit<Transaction, 'id' | 'month' | 'originalDescription'> & ShareOnCreate & {
  month?: YearMonth
  originalDescription?: string | null
}

/** `expense` sem os investimentos (categoria "Investir"/"Investimento"); `balance` desconta os dois. */
export type Totals = { income: number; expense: number; invested: number; balance: number }

/** Item da categoria no mês; ocasionais agrupados pela descrição. */
export type CategoryItem = { description: string; fixed: boolean; count: number; total: number }

export type CategoryBreakdown = {
  categoryId: string
  name: string
  color: string
  fixed: number
  occasional: number
  total: number
}

export type MonthlySummary = {
  month: YearMonth
  fixed: Totals
  occasional: Totals
  total: Totals
  fixedCommitmentPercent: number | null
  /** % dos ganhos do mês que foi investido; nulo sem ganhos */
  investedPercent: number | null
  /** Sem as categorias de investimento */
  expensesByCategory: CategoryBreakdown[]
  incomesByCategory: CategoryBreakdown[]
}

export type MonthPoint = {
  month: YearMonth
  fixedIncome: number
  fixedExpense: number
  occasionalIncome: number
  occasionalExpense: number
  /** Investido no mês (fixos + ocasionais), fora das despesas */
  invested: number
  balance: number
}

export type ImportRequest = {
  transactions: TransactionRequest[]
  fixedAdjustments: (FixedAdjustment & { recurringEntryId: string })[]
}

export type ImportResponse = { transactions: number; fixedAdjustments: number }
