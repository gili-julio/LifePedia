import type { YearMonth } from '@/lib/format'

/** Espelha `finance/wallet/CardCycle.java`: fatura = mês do vencimento; compra no dia do fechamento já vai para a seguinte. */
export type Cycle = { closingDay: number; dueDay: number }

function at(month: YearMonth, day: number) {
  const [year, m] = month.split('-').map(Number)
  const last = new Date(year, m, 0).getDate()
  return `${month}-${String(Math.min(day, last)).padStart(2, '0')}`
}

function shift(month: YearMonth, delta: number): YearMonth {
  const [year, m] = month.split('-').map(Number)
  const date = new Date(year, m - 1 + delta, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export function dueOn(cycle: Cycle, month: YearMonth) {
  return at(month, cycle.dueDay)
}

export function closesOn(cycle: Cycle, month: YearMonth) {
  const closing = at(month, cycle.closingDay)
  return closing < dueOn(cycle, month) ? closing : at(shift(month, -1), cycle.closingDay)
}

/** Fatura de uma compra feita em `date` (yyyy-MM-dd); parcela n vai n-1 faturas depois. */
export function invoiceOf(cycle: Cycle, date: string, installment = 1): YearMonth {
  let month = date.slice(0, 7)
  while (date >= closesOn(cycle, month)) month = shift(month, 1)
  return shift(month, Math.max(0, installment - 1))
}
