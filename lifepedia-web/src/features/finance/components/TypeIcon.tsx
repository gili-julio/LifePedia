import { ArrowDownRightIcon, ArrowUpRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { TYPE_LABEL } from '../categories'
import type { EntryType } from '../types'

/** Ícone de ganho/despesa com rótulo acessível. */
export function TypeIcon({ type, className }: { type: EntryType; className?: string }) {
  const Icon = type === 'INCOME' ? ArrowUpRightIcon : ArrowDownRightIcon
  return (
    <span
      title={TYPE_LABEL[type]}
      className={cn(
        'inline-flex size-8 shrink-0 items-center justify-center rounded-full',
        type === 'INCOME' ? 'bg-income/12 text-income' : 'bg-expense/12 text-expense',
        className,
      )}
    >
      <Icon className="size-4" aria-hidden />
      <span className="sr-only">{TYPE_LABEL[type]}</span>
    </span>
  )
}
