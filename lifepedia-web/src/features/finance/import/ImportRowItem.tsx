import { AlertTriangleIcon, ChevronRightIcon } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { formatDate, formatMonth, parseAmount } from '@/lib/format'
import { cn } from '@/lib/utils'
import { FALLBACK_COLOR } from '../categories'
import { ColorDot } from '../components/CategoryBadge'
import { Money } from '../components/Money'
import type { Category, RecurringEntry } from '../types'
import { FixedOutcomeNote } from './FixedOutcomeNote'
import { OriginalDescription } from './OriginalDescription'
import type { ImportRow } from './parse'
import { SOURCE_LABEL } from './prompt'

type Props = {
  row: ImportRow
  errors: string[]
  category?: Category
  fixed?: RecurringEntry
  onToggle: (include: boolean) => void
  onOpen: () => void
}

/** Mostra o mês em que a linha conta quando não é o da data (fatura). */
export function CountsIn({ row, className }: { row: ImportRow; className?: string }) {
  if (!row.month || !row.date || row.month === row.date.slice(0, 7)) return null
  return <p className={cn('text-xs text-muted-foreground', className)}>Conta em {formatMonth(row.month, 'short')}</p>
}

/** Linha da prévia no mobile: toque abre a edição. */
export function ImportRowItem({ row, errors, category, fixed, onToggle, onOpen }: Props) {
  const amount = parseAmount(row.amount)
  const date = row.date ? formatDate(row.date).slice(0, 5) : 'sem data'
  return (
    <li className={cn('flex items-start gap-3 py-3', !row.include && 'opacity-50')}>
      <Checkbox className="mt-1" aria-label={`Considerar ${row.description}`} checked={row.include}
        onCheckedChange={(v) => onToggle(v === true)} />
      <button type="button" onClick={onOpen} className="flex min-w-0 flex-1 items-start gap-2 text-left">
        <div className="min-w-0 flex-1 space-y-0.5">
          <div className="flex items-baseline justify-between gap-2">
            <p className="truncate font-medium">{row.description || 'Sem descrição'}</p>
            <span className="shrink-0 text-sm font-medium">
              {amount > 0 ? <Money value={amount} type={row.type} /> : '—'}
            </span>
          </div>
          <OriginalDescription original={row.originalDescription} description={row.description} />
          <p className="flex items-center gap-1.5 truncate text-xs text-muted-foreground">
            {fixed ? (
              <>Fixo: {fixed.description} · {date} · {SOURCE_LABEL[row.source]}</>
            ) : (
              <>
                <ColorDot color={category?.color ?? FALLBACK_COLOR} />
                {category?.name ?? 'Sem categoria'} · {date} · {SOURCE_LABEL[row.source]}
              </>
            )}
          </p>
          <CountsIn row={row} />
          {fixed && row.include && <FixedOutcomeNote row={row} fixed={fixed} />}
          {errors.length > 0 && <p className="text-xs text-destructive">Corrigir: {errors.join(', ')}</p>}
          {row.warnings.map((w) => (
            <p key={w} className="flex items-center gap-1 text-xs text-muted-foreground">
              <AlertTriangleIcon className="size-3 shrink-0" /> {w}
            </p>
          ))}
        </div>
        <ChevronRightIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
      </button>
    </li>
  )
}
