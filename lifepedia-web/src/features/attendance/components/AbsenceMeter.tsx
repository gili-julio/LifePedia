import { cn } from '@/lib/utils'
import { absenceTone, remainingLabel, TONE_BAR, TONE_TEXT } from '../absences'

/** Barra de faltas usadas do limite, azul, laranja perto do limite e vermelha ao passar (sempre com texto). */
export function AbsenceMeter({ absences, max, compact = false }: { absences: number; max: number; compact?: boolean }) {
  const tone = absenceTone(absences, max)
  const pct = max > 0 ? Math.min(100, (absences / max) * 100) : absences > 0 ? 100 : 0
  return (
    <div className="space-y-1">
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="font-medium tabular-nums">Faltas {absences} de {max}</span>
        {!compact && <span className={cn('text-xs', TONE_TEXT[tone])}>{remainingLabel(absences, max)}</span>}
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-muted" role="meter" aria-valuemin={0} aria-valuemax={max}
        aria-valuenow={absences} aria-label={`Faltas: ${absences} de ${max}. ${remainingLabel(absences, max)}`}>
        <div className={cn('h-full rounded-full transition-[width] motion-reduce:transition-none', TONE_BAR[tone])}
          style={{ width: `${pct}%` }} />
      </div>
      {compact && <p className={cn('text-xs', TONE_TEXT[tone])}>{remainingLabel(absences, max)}</p>}
    </div>
  )
}
