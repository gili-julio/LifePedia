import type { AttendanceAgendaItem } from '@/features/attendance/types'
import { isoWeekday, toMinutes } from '../dates'
import type { AgendaDay } from '../types'

const DAY_MINUTES = 24 * 60

/** Conta do dia: ocupado = união dos horários das tarefas e compromissos (o que se cruza conta uma vez). */
export type DayMetrics = { date: string; busy: number; sleep: number; free: number; tasks: number; commitments: number }

export type WeekMetrics = {
  days: DayMetrics[]
  busy: number
  weekdays: number
  weekend: number
  sleep: number
  free: number
  tasks: number
  commitments: number
}

/**
 * Indicadores da semana a partir da agenda: tarefa com horário e sem duração vale `defaultMinutes` (como na grade);
 * puladas e sem horário não contam; compromissos da Frequência entram (menos os "sem aula"); o que passa da
 * meia-noite fica no próprio dia.
 */
export function weekMetrics(days: AgendaDay[], sleepMinutes: number[], defaultMinutes: number,
  commitments: AttendanceAgendaItem[] = []): WeekMetrics {
  const perDay = days.map(({ date, items }) => {
    const timed = items.filter((i) => i.dueTime && i.status !== 'SKIPPED')
    const held = commitments.filter((c) => c.date === date && c.status !== 'CANCELED')
    const busy = union([
      ...timed.map((i) => [toMinutes(i.dueTime as string), i.durationMinutes ?? defaultMinutes] as const),
      ...held.map((c) => [toMinutes(c.time), c.durationMinutes] as const),
    ])
    const sleep = sleepMinutes[isoWeekday(date) - 1] ?? 0
    return {
      date, busy, sleep, free: Math.max(0, DAY_MINUTES - sleep - busy), tasks: timed.length, commitments: held.length,
    }
  })
  const sum = (list: DayMetrics[], pick: (d: DayMetrics) => number) => list.reduce((total, d) => total + pick(d), 0)
  const weekend = perDay.filter((d) => isoWeekday(d.date) >= 6)
  const weekdays = perDay.filter((d) => isoWeekday(d.date) < 6)
  return {
    days: perDay,
    busy: sum(perDay, (d) => d.busy),
    weekdays: sum(weekdays, (d) => d.busy),
    weekend: sum(weekend, (d) => d.busy),
    sleep: sum(perDay, (d) => d.sleep),
    free: sum(perDay, (d) => d.free),
    tasks: sum(perDay, (d) => d.tasks),
    commitments: sum(perDay, (d) => d.commitments),
  }
}

/** "12h30", "8h", "45min" ou "0h". */
export function formatHours(minutes: number) {
  const total = Math.round(minutes)
  const h = Math.floor(total / 60)
  const m = total % 60
  if (!h) return m ? `${m}min` : '0h'
  return m ? `${h}h${String(m).padStart(2, '0')}` : `${h}h`
}

/** Minutos cobertos por `[início, duração]` no dia. */
function union(blocks: (readonly [number, number])[]) {
  const spans = blocks
    .map(([start, minutes]) => [start, Math.min(start + minutes, DAY_MINUTES)] as const)
    .sort((a, b) => a[0] - b[0])
  let total = 0
  let end = -1
  for (const [start, stop] of spans) {
    if (stop <= end) continue
    total += stop - Math.max(start, end)
    end = stop
  }
  return total
}
