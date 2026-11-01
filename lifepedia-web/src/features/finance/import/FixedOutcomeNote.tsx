import { LandmarkIcon, RepeatIcon } from 'lucide-react'
import { bankLabel } from '@/lib/banks'
import { useMoney } from '@/lib/hide-values'
import type { RecurringEntry } from '../types'
import { fixedOutcome, rowBank, type ImportRow } from './parse'

/** Explica o efeito de um item ligado a fixo. */
export function FixedOutcomeNote({ row, fixed }: { row: ImportRow; fixed: RecurringEntry }) {
  const { formatCurrency } = useMoney()
  if (!row.date) return null
  const { current, amount, changes } = fixedOutcome(row, fixed)
  const bank = rowBank(row)
  const differs = bank && fixed.bank && (bank.code !== fixed.bank.code || bank.name !== fixed.bank.name)
  return (
    <>
      <p className="flex items-center gap-1 text-xs text-muted-foreground">
        <RepeatIcon className="size-3 shrink-0" />
        {changes
          ? `Valor real no mês: ${formatCurrency(amount)} (fixo: ${formatCurrency(current)})`
          : 'Já conta no mês pelo fixo; nada a lançar.'}
      </p>
      {bank && (!fixed.bank || differs) && (
        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          <LandmarkIcon className="size-3 shrink-0" />
          {fixed.bank
            ? `O fixo está em ${bankLabel(fixed.bank)} e continua assim; aqui veio ${bankLabel(bank)}.`
            : `O fixo passa a ser de ${bankLabel(bank)}.`}
        </p>
      )}
    </>
  )
}
