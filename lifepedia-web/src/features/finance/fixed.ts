import type { YearMonth } from '@/lib/format'
import type { RecurringEntry } from './types'

export function isActiveIn(entry: RecurringEntry, month: YearMonth) {
  return entry.active && entry.startMonth <= month && (!entry.endMonth || entry.endMonth >= month)
}

/** Valor do fixo no mês: o real (ajustado), se houver; senão o cadastrado. */
export function valueIn(entry: RecurringEntry, month: YearMonth) {
  return entry.adjustments.find((a) => a.month === month)?.amount ?? entry.amount
}
