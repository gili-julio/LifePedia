import { HourglassIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { laneDaysText, laneTone } from '../labels'
import type { DevItem } from '../types'

const TONE = {
  normal: 'border-border bg-muted text-foreground',
  warning: 'border-amber-500/40 bg-amber-500/10 text-amber-800 dark:text-amber-300',
  danger: 'border-destructive/40 bg-destructive/10 text-destructive',
}

/** Raia da issue e há quantos dias está nela; amarelo ao passar do limite, vermelho no dobro. */
export function LaneBadge({ item, showLane = true }: { item: Pick<DevItem, 'lane' | 'laneDays' | 'laneLimit'>; showLane?: boolean }) {
  if (!item.lane) return null
  const tone = laneTone(item.laneDays, item.laneLimit)
  const days = laneDaysText(item.laneDays)
  if (!showLane && !days) return null
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium', TONE[tone])}
      title={item.laneLimit ? `Limite de ${item.laneLimit} dia${item.laneLimit > 1 ? 's' : ''} nesta raia` : undefined}>
      {tone !== 'normal' && <HourglassIcon className="size-3" aria-hidden />}
      {showLane ? item.lane : null}{showLane && days ? ' · ' : ''}{days}
      {tone !== 'normal' && <span className="sr-only"> (parada além do limite)</span>}
    </span>
  )
}
