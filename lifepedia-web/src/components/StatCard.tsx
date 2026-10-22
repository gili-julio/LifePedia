import type { ComponentType, ReactNode } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

/** Ganho (verde), perda (vermelho) ou dado/indicador (azul). */
export type StatTone = 'income' | 'expense' | 'data'

const TONE: Record<StatTone, { card: string; label: string; icon: string }> = {
  income: { card: 'bg-income/10 ring-income/20', label: 'text-income', icon: 'bg-income/15 text-income' },
  expense: { card: 'bg-expense/10 ring-expense/20', label: 'text-expense', icon: 'bg-expense/15 text-expense' },
  data: { card: 'bg-data/10 ring-data/20', label: 'text-data', icon: 'bg-data/15 text-data' },
}

type Props = {
  label: string
  tone: StatTone
  icon?: ComponentType<{ className?: string }>
  /** Sem valor (`undefined`) mostra carregando. */
  children?: ReactNode
  hint?: ReactNode
}

/** Total ou indicador em card tingido pela cor do que representa. */
export function StatCard({ label, tone, icon: Icon, children, hint }: Props) {
  const style = TONE[tone]
  return (
    <Card size="sm" className={style.card}>
      <CardContent className="space-y-1">
        <div className="flex items-center justify-between gap-2 text-sm font-semibold">
          <span className={style.label}>{label}</span>
          {Icon && (
            <span className={cn('flex size-8 shrink-0 items-center justify-center rounded-full', style.icon)}>
              <Icon className="size-4" aria-hidden />
            </span>
          )}
        </div>
        {children === undefined ? <Skeleton className="h-8 w-32" /> : (
          <div className={cn('text-2xl font-bold tracking-tight', style.label)}>{children}</div>
        )}
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}
