import { formatBytes } from '@/lib/format'
import type { Usage } from '../types'

/** Espaço usado pelas maquetes e anexos dos projetos do dono. */
export function UsageBar({ usage, label = 'Seu espaço' }: { usage: Usage; label?: string }) {
  const used = usage.quotaBytes ? Math.min(100, Math.round((usage.usedBytes / usage.quotaBytes) * 100)) : 0
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span>{label}</span>
        <span className="tabular-nums text-muted-foreground">{formatBytes(usage.usedBytes)} de {formatBytes(usage.quotaBytes)}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-muted" role="meter" aria-valuemin={0} aria-valuemax={usage.quotaBytes}
        aria-valuenow={usage.usedBytes} aria-label={label}>
        <div className={used >= 90 ? 'h-full bg-destructive' : 'h-full bg-data'} style={{ width: `${used}%` }} />
      </div>
    </div>
  )
}
