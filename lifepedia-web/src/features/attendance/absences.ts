import type { DayStatus } from './types'

/** `near`: já faltou e sobram até 2 (ou nenhuma); `over`: passou do limite. */
export type AbsenceTone = 'ok' | 'near' | 'over'

export function absenceTone(absences: number, max: number): AbsenceTone {
  const remaining = max - absences
  if (remaining < 0) return 'over'
  return absences > 0 && remaining <= 2 ? 'near' : 'ok'
}

/** Texto do quanto sobra (a cor nunca vem sozinha). */
export function remainingLabel(absences: number, max: number) {
  const remaining = max - absences
  if (remaining < 0) return `Passou do limite em ${-remaining} ${-remaining === 1 ? 'falta' : 'faltas'}`
  if (remaining === 0) return 'No limite: nenhuma falta sobrando'
  return `Pode faltar mais ${remaining}`
}

export const TONE_BAR: Record<AbsenceTone, string> = {
  ok: 'bg-data',
  near: 'bg-orange-500',
  over: 'bg-destructive',
}

export const TONE_TEXT: Record<AbsenceTone, string> = {
  ok: 'text-muted-foreground',
  near: 'text-orange-700 dark:text-orange-300',
  over: 'text-destructive',
}

export const STATUS_LABEL: Record<DayStatus, string> = {
  PRESENT: 'Presente',
  ABSENT: 'Falta',
  CANCELED: 'Sem aula',
  PENDING: 'Pendente',
  UPCOMING: 'A seguir',
}

const WEEKDAYS = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom']

/** "seg, qua · 08:00 às 09:40". */
export function scheduleLabel(weekdays: number[], time: string, durationMinutes: number) {
  return `${weekdays.map((d) => WEEKDAYS[d - 1]).join(', ')} · ${time} às ${endOf(time, durationMinutes)}`
}

export function endOf(time: string, durationMinutes: number) {
  const [h, m] = time.split(':').map(Number)
  const total = (h * 60 + m + durationMinutes) % (24 * 60)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}
