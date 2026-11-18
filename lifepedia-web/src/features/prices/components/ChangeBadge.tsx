import { ArrowDownRightIcon, ArrowUpRightIcon, MinusIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { formatChange } from '../labels'

/** Variação do menor preço (queda em verde, alta em vermelho), com o período em texto. */
export function ChangeBadge({ value, period, className }: { value: number | null; period: string; className?: string }) {
  const text = formatChange(value)
  if (text === null || value === null) return null
  const Icon = value < 0 ? ArrowDownRightIcon : value > 0 ? ArrowUpRightIcon : MinusIcon
  const label = value < 0 ? `caiu ${text.slice(1)} ${period}` : value > 0 ? `subiu ${text.slice(1)} ${period}` : `igual ${period}`
  return (
    <span title={label} aria-label={label}
      className={cn('inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-[11px] font-medium tabular-nums',
        value < 0 ? 'bg-income/10 text-income' : value > 0 ? 'bg-expense/10 text-expense' : 'bg-muted text-muted-foreground', className)}>
      <Icon className="size-3" aria-hidden /> {text} <span className="font-normal opacity-80">{period}</span>
    </span>
  )
}
