import { formatCurrency } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { EntryType } from '../types'

type Props = {
  value: number
  /** Prefixa +/− conforme o tipo. */
  type?: EntryType
  /** Saldo: destaca valores negativos. */
  signed?: boolean
  className?: string
}

/** Valor em cor neutra; saldo negativo em vermelho. */
export function Money({ value, type, signed, className }: Props) {
  const prefix = type === 'INCOME' ? '+ ' : type === 'EXPENSE' ? '− ' : ''
  return (
    <span className={cn('tabular-nums', signed && value < 0 && 'text-destructive', className)}>
      {prefix + formatCurrency(value)}
    </span>
  )
}
