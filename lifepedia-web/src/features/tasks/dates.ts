import { formatDate } from '@/lib/format'
import type { Priority, RepeatRule } from './types'

const pad = (n: number) => String(n).padStart(2, '0')

function toDate(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function toIso(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function addDays(iso: string, days: number) {
  const date = toDate(iso)
  date.setDate(date.getDate() + days)
  return toIso(date)
}

/** Próxima segunda depois de `iso`. */
export function nextMonday(iso: string) {
  return addDays(iso, 8 - isoWeekday(iso))
}

/** 1 = segunda ... 7 = domingo. */
export function isoWeekday(iso: string) {
  return toDate(iso).getDay() || 7
}

export const WEEKDAYS_SHORT = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom']
const WEEKDAYS_LONG = ['segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado', 'domingo']

/** "Hoje", "Amanhã", "Ontem" ou "qua, 01/10". */
export function dayLabel(iso: string, today: string) {
  if (iso === today) return 'Hoje'
  if (iso === addDays(today, 1)) return 'Amanhã'
  if (iso === addDays(today, -1)) return 'Ontem'
  return `${WEEKDAYS_SHORT[isoWeekday(iso) - 1]}, ${formatDate(iso).slice(0, 5)}`
}

/** "3ª terça" do dia (5ª vira "última", como na API). */
export function nthWeekday(iso: string) {
  const nth = Math.floor((toDate(iso).getDate() - 1) / 7) + 1
  const name = WEEKDAYS_LONG[isoWeekday(iso) - 1]
  return nth >= 5 ? `última ${name}` : `${nth}ª ${name}`
}

export const UNIT_LABEL: Record<RepeatRule['unit'], [string, string]> = {
  DAY: ['dia', 'dias'],
  WEEK: ['semana', 'semanas'],
  MONTH: ['mês', 'meses'],
  YEAR: ['ano', 'anos'],
}

/** Texto da regra: "Toda semana: seg, qua", "Todo mês no dia 10", "3 dias após concluir"... */
export function describeRepeat(rule: RepeatRule, start: string | null) {
  const [one, many] = UNIT_LABEL[rule.unit]
  let text = rule.interval === 1
    ? `${rule.unit === 'WEEK' ? 'Toda' : 'Todo'} ${one}`
    : `A cada ${rule.interval} ${many}`
  if (rule.fromCompletion) {
    text = `${rule.interval} ${rule.interval === 1 ? one : many} após concluir`
  } else if (rule.unit === 'WEEK' && rule.weekdays.length) {
    text += `: ${rule.weekdays.map((d) => WEEKDAYS_SHORT[d - 1]).join(', ')}`
  } else if (rule.unit === 'MONTH' && start) {
    if (rule.monthMode === 'LAST_DAY') text += ' no último dia'
    else if (rule.monthMode === 'WEEKDAY') text += ` na ${nthWeekday(start)}`
    else text += ` no dia ${Number(start.slice(8))}`
  } else if (rule.unit === 'YEAR' && start) {
    text += ` em ${formatDate(start).slice(0, 5)}`
  }
  if (rule.until) text += ` · até ${formatDate(rule.until)}`
  if (rule.count) text += ` · ${rule.count} ${rule.count === 1 ? 'vez' : 'vezes'}`
  return text
}

/** "09:30 · 45 min" / "09:30 · 1h30". */
export function formatTime(time: string | null, minutes: number | null) {
  if (!time) return null
  if (!minutes) return time
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${time} · ${h ? `${h}h${m ? pad(m) : ''}` : `${m} min`}`
}

export const PRIORITY_LABEL: Record<Priority, string> = {
  NONE: 'Nenhuma',
  LOW: 'Baixa',
  MEDIUM: 'Média',
  HIGH: 'Alta',
}

/** Cor da marca de prioridade (borda do círculo, bandeira); nunca em texto. */
export const PRIORITY_COLOR: Record<Priority, string | null> = {
  NONE: null,
  LOW: '#2a78d6',
  MEDIUM: '#eda100',
  HIGH: '#e34948',
}

/** Opções de lembrete (minutos antes do horário). */
export const REMINDERS: { minutes: number; label: string }[] = [
  { minutes: 0, label: 'Na hora' },
  { minutes: 5, label: '5 min antes' },
  { minutes: 15, label: '15 min antes' },
  { minutes: 30, label: '30 min antes' },
  { minutes: 60, label: '1 hora antes' },
  { minutes: 1440, label: '1 dia antes' },
]

export function reminderLabel(minutes: number) {
  return REMINDERS.find((r) => r.minutes === minutes)?.label ?? `${minutes} min antes`
}

/** Segunda-feira da semana de `iso`. */
export function mondayOf(iso: string) {
  return addDays(iso, 1 - isoWeekday(iso))
}
