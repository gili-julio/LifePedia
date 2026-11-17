import { parseAmount, todayIso } from '@/lib/format'
import type { Period, SearchFilters } from './types'

const NUMBER = String.raw`\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?`
const GREATER = new RegExp(String.raw`(?:^|\s)(?:>=?|acima de|mais de)\s*(?:r\$\s*)?(${NUMBER})(?=\s|$)`, 'i')
const LESS = new RegExp(String.raw`(?:^|\s)(?:<=?|abaixo de|menos de|até)\s*(?:r\$\s*)?(${NUMBER})(?=\s|$)`, 'i')
const RANGE = new RegExp(String.raw`(?:^|\s)(?:r\$\s*)?(${NUMBER})\s*(?:-|a)\s*(?:r\$\s*)?(${NUMBER})(?=\s|$)`, 'i')
const EXACT = new RegExp(String.raw`(?:^|\s)(?:r\$\s*(${NUMBER})|(\d+,\d{1,2}))(?=\s|$)`, 'i')

export type ParsedQuery = { text: string; min?: number; max?: number }

/** Tira do texto os valores digitados: "> 100", "< 50", "100-200", "R$ 50" ou "50,90" (valor exato). */
export function parseQuery(raw: string): ParsedQuery {
  let text = raw
  let min: number | undefined
  let max: number | undefined
  const take = (pattern: RegExp) => {
    const match = text.match(pattern)
    if (match) text = text.replace(match[0], ' ')
    return match
  }
  const range = take(RANGE)
  if (range) {
    min = parseAmount(range[1])
    max = parseAmount(range[2])
    if (min > max) [min, max] = [max, min]
  }
  const greater = take(GREATER)
  if (greater) min = parseAmount(greater[1])
  const less = take(LESS)
  if (less) max = parseAmount(less[1])
  if (min === undefined && max === undefined) {
    const exact = take(EXACT)
    if (exact) min = max = parseAmount(exact[1] ?? exact[2])
  }
  return { text: text.replace(/\s+/g, ' ').trim(), min, max }
}

/** Datas `from`/`to` do período (até hoje). */
export function periodRange(period: Period): { from?: string; to?: string } {
  if (period === 'all') return {}
  const today = new Date()
  const from = new Date(today)
  if (period === 'month') from.setDate(1)
  else if (period === '30d') from.setDate(from.getDate() - 29)
  else if (period === '3m') from.setMonth(from.getMonth() - 3)
  else if (period === 'year') from.setMonth(0, 1)
  return { from: iso(from), to: todayIso() }
}

/** Parâmetros da API para o texto e os filtros. */
export function searchParams(raw: string, filters: SearchFilters, limit?: number) {
  const parsed = parseQuery(raw)
  return {
    q: parsed.text,
    modules: filters.modules.join(',') || undefined,
    kinds: filters.kinds?.join(',') || undefined,
    groupId: filters.groupId,
    ...periodRange(filters.period),
    min: filters.min ?? parsed.min,
    max: filters.max ?? parsed.max,
    type: filters.type,
    limit,
  }
}

export function hasCriteria(params: ReturnType<typeof searchParams>) {
  return params.q.length >= 2 || params.from !== undefined || params.min !== undefined || params.max !== undefined
    || params.type !== undefined
}

function iso(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
