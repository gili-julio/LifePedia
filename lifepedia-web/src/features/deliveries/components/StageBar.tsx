import { AlertTriangleIcon } from 'lucide-react'
import type { CSSProperties } from 'react'
import { cn } from '@/lib/utils'
import { STAGES, STATUS_LABEL, stageLabel } from '../labels'
import type { Delivery } from '../types'

/** Postado → Em trânsito → Saiu para entrega → Entregue, com a etapa atual destacada (e alerta em problema). */
export function StageBar({ delivery, compact = false }: { delivery: Delivery; compact?: boolean }) {
  const current = delivery.stage
  const done = delivery.status === 'DELIVERED'
  const fraction = current <= 0 ? 0 : current / (STAGES.length - 1)
  const style = { '--dot': compact ? '1.5rem' : '2rem' } as CSSProperties
  return (
    <div className={cn('relative', !compact && 'pb-9')} style={style}>
      <span aria-hidden className="absolute inset-x-[calc(var(--dot)/2)] top-[calc(var(--dot)/2-1px)] h-0.5 rounded-full bg-border" />
      <span aria-hidden className={cn('absolute left-[calc(var(--dot)/2)] top-[calc(var(--dot)/2-1px)] h-0.5 rounded-full',
        done ? 'bg-income' : 'bg-data')} style={{ width: `calc((100% - var(--dot)) * ${fraction})` }} />
      <ol className="relative flex justify-between" aria-label={`Andamento: ${STATUS_LABEL[delivery.status]}`}>
        {STAGES.map((stage, i) => {
          const reached = i <= current
          const active = i === current
          const problem = active && delivery.problem
          const Icon = problem ? AlertTriangleIcon : stage.icon
          return (
            <li key={stage.label} aria-current={active ? 'step' : undefined} className="relative flex w-[var(--dot)] flex-col items-center">
              <span className={cn('flex size-[var(--dot)] items-center justify-center rounded-full border-2 bg-card',
                problem ? 'border-destructive text-destructive'
                  : reached ? (done ? 'border-income bg-income text-white' : active ? 'border-data bg-data text-white' : 'border-data text-data')
                  : 'border-border text-muted-foreground')}>
                <Icon className={compact ? 'size-3' : 'size-4'} aria-hidden />
              </span>
              {compact ? <span className="sr-only">{stageLabel(delivery, i)}{active ? ' (agora)' : ''}</span> : (
                <span className={cn('absolute top-full mt-1 w-20 text-[11px] leading-tight sm:text-xs',
                  i === 0 ? 'left-0 text-left' : i === STAGES.length - 1 ? 'right-0 text-right' : 'left-1/2 -translate-x-1/2 text-center',
                  active ? (problem ? 'font-semibold text-destructive' : 'font-semibold') : 'text-muted-foreground')}>
                  {stageLabel(delivery, i)}
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </div>
  )
}
