import { isoWeekday, toMinutes } from '../dates'
import type { AgendaDay, TaskItem } from '../types'

const DAY_MINUTES = 24 * 60

/** Conta do dia: ocupado = união dos horários das tarefas (o que se cruza conta uma vez). */
export type DayMetrics = { date: string; busy: number; sleep: number; free: number; tasks: number }

export type WeekMetrics = {
  days: DayMetrics[]
  busy: number
  weekdays: number
  weekend: number
  sleep: number
  free: number
  tasks: number
}

/**
 * Indicadores da semana a partir da agenda: tarefa com horário e sem duração vale `defaultMinutes` (como na grade);
 * puladas e sem horário não contam; o que passa da meia-noite fica no próprio dia.
 */
export function weekMetrics(days: AgendaDay[], sleepMinutes: number[], defaultMinutes: number): WeekMetrics {
  const perDay = days.map(({ date, items }) => {
    const timed = items.filter((i) => i.dueTime && i.status !== 'SKIPPED')
    const busy = union(timed, defaultMinutes)
    const sleep = sleepMinutes[isoWeekday(date) - 1] ?? 0
    return { date, busy, sleep, free: Math.max(0, DAY_MINUTES - sleep - busy), tasks: timed.length }
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

function union(items: TaskItem[], defaultMinutes: number) {
  const spans = items
    .map((i) => {
      const start = toMinutes(i.dueTime as string)
      return [start, Math.min(start + (i.durationMinutes ?? defaultMinutes), DAY_MINUTES)] as const
    })
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
