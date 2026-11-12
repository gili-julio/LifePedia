import type { CalendarEvent } from './types'

/** Dia local (`yyyy-MM-dd`) em que o evento começa. */
export function eventDay(event: CalendarEvent) {
  const d = new Date(event.start)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Início e fim em minutos desde 0h do dia em que começa (o que passa da meia-noite fica no fim do dia). */
export function eventMinutes(event: CalendarEvent) {
  const start = new Date(event.start)
  const startMin = start.getHours() * 60 + start.getMinutes()
  const length = Math.max(15, Math.round((Date.parse(event.end) - start.getTime()) / 60_000))
  return { start: startMin, end: Math.min(24 * 60, startMin + length) }
}
