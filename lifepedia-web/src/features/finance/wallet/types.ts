import type { UserSummary } from '@/features/groups/types'
import type { BankRef } from '@/lib/banks'
import type { YearMonth } from '@/lib/format'
import type { EntryType } from '../types'

/** Espelha `finance/wallet/InvoiceStatus`. */
export type InvoiceStatus = 'FUTURE' | 'OPEN' | 'CLOSED' | 'PAID' | 'OVERDUE'

/** Fatura resumida; `daysToClose`/`daysToDue` negativos = já fechou/venceu. */
export type InvoiceSummary = {
  month: YearMonth
  status: InvoiceStatus
  opensOn: string
  closesOn: string
  dueOn: string
  total: number
  paidOn: string | null
  paidAmount: number | null
  daysToClose: number
  daysToDue: number
}

export type Limit = { limit: number; used: number; available: number }

export type GroupRef = { id: string; name: string }

/** Cartão com a fatura aberta e a fechada ainda não paga (`pending`). `key` filtra os lançamentos do banco. */
export type CardView = {
  id: string
  key: string
  bank: BankRef
  label: string
  nickname: string | null
  lastDigits: string | null
  color: string | null
  closingDay: number
  dueDay: number
  creditLimit: number | null
  budget: number | null
  reminders: boolean
  accountId: string | null
  groups: GroupRef[]
  current: InvoiceSummary
  pending: InvoiceSummary | null
  limit: Limit | null
}

/** `today` = saldo informado + o que entrou e saiu depois; `endOfMonth` também desconta as faturas a pagar. */
export type AccountView = {
  id: string
  key: string
  bank: BankRef
  label: string
  nickname: string | null
  balance: number
  balanceOn: string
  today: number
  endOfMonth: number
  pendingInvoices: number
}

export type Wallet = { cards: CardView[]; accounts: AccountView[]; openTotal: number; toPay: number; today: string }

/** Ocasional (`transactionId`) ou fixo (`recurringId`). */
export type InvoiceItem = {
  transactionId: string | null
  recurringId: string | null
  description: string
  date: string
  type: EntryType
  amount: number
  category: string | null
  color: string | null
  checked: boolean
  installment: string | null
}

/** `projected` nulo no começo do ciclo; `average` = 3 últimas faturas com valor. */
export type Projection = {
  projected: number | null
  average: number | null
  cycleDays: number
  elapsedDays: number
  budgetPercent: number | null
}

export type MonthTotal = { month: YearMonth; total: number; status: InvoiceStatus }

export type InvoiceDetail = {
  summary: InvoiceSummary
  items: InvoiceItem[]
  checked: number
  count: number
  purchases: number
  committed: number
  credits: number
  projection: Projection
}

export type CardDetail = { card: CardView; invoice: InvoiceDetail; history: MonthTotal[]; upcoming: MonthTotal[] }

export type CardRequest = {
  bank: BankRef
  nickname: string | null
  lastDigits: string | null
  color: string | null
  closingDay: number
  dueDay: number
  creditLimit: number | null
  budget: number | null
  reminders: boolean
  accountId: string | null
  groupIds: string[]
}

export type AccountRequest = { bank: BankRef; nickname: string | null; balance: number; balanceOn: string }

export type PurchaseRequest = {
  cardId?: string
  accountId?: string
  description: string
  amount: number
  date: string
  categoryId?: string
  installments: number
  groupIds?: string[]
}

export type PurchaseResult = { created: number; firstMonth: YearMonth; categoryId: string }

export type RecalculateItem = {
  id: string
  description: string
  date: string
  amount: number
  month: YearMonth
  expected: YearMonth
}

/** Cartão de um membro no grupo; `groupSpent` = gastos do grupo que ele lançou neste cartão na fatura aberta. */
export type GroupCard = {
  id: string
  owner: UserSummary | null
  mine: boolean
  label: string
  bank: BankRef
  lastDigits: string | null
  color: string | null
  current: InvoiceSummary
  groupSpent: number
}
