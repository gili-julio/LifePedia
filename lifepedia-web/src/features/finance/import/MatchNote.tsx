import { CheckCircle2Icon } from 'lucide-react'
import { formatDate, formatMonth, parseAmount } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import { rowMonth, type ImportRow } from './parse'

/** Linha casada com um lançamento feito à mão: diz o que muda nele ao importar. */
export function MatchNote({ row, onUnlink, className }: { row: ImportRow; onUnlink?: () => void; className?: string }) {
  const { formatCurrency } = useMoney()
  const match = row.match
  if (!match || !row.include) return null
  const differs = Math.abs(match.amount - parseAmount(row.amount)) >= 0.005
  const month = rowMonth(row)
  const moves = month !== match.month
  return (
    <div className={cn('rounded-md bg-income/10 px-2 py-1.5 text-xs', className)}>
      <p className="flex gap-1.5">
        <CheckCircle2Icon className="mt-px size-3.5 shrink-0 text-income" aria-hidden />
        <span>
          Você já lançou "{match.description}" em {formatDate(match.date).slice(0, 5)}. Não vai duplicar: ele fica conferido
          {differs && `, com o valor do documento (era ${formatCurrency(match.amount)})`}
          {moves && `, contando em ${formatMonth(month, 'short')}`}.
        </span>
      </p>
      {onUnlink && (
        <button type="button" className="mt-1 font-medium underline underline-offset-4" onClick={onUnlink}>
          Não é o mesmo: lançar como novo
        </button>
      )}
    </div>
  )
}
