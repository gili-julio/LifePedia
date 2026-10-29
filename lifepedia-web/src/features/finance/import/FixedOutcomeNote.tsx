import { RepeatIcon } from 'lucide-react'
import { useMoney } from '@/lib/hide-values'
import type { RecurringEntry } from '../types'
import { fixedOutcome, type ImportRow } from './parse'

/** Explica o efeito de um item ligado a fixo. */
export function FixedOutcomeNote({ row, fixed }: { row: ImportRow; fixed: RecurringEntry }) {
  const { formatCurrency } = useMoney()
  if (!row.date) return null
  const { current, amount, changes } = fixedOutcome(row, fixed)
  return (
    <p className="flex items-center gap-1 text-xs text-muted-foreground">
      <RepeatIcon className="size-3 shrink-0" />
      {changes
        ? `Valor real no mês: ${formatCurrency(amount)} (fixo: ${formatCurrency(current)})`
        : 'Já conta no mês pelo fixo; nada a lançar.'}
    </p>
  )
}
