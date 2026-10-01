import { formatDistanceToNow } from 'date-fns'
import { ptBR } from 'date-fns/locale'

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const compactCurrency = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  maximumFractionDigits: 1,
})

export function formatCurrency(value: number) {
  return currency.format(value)
}

export function formatCompactCurrency(value: number) {
  return compactCurrency.format(value)
}

/** "2026-09" (formato da API) */
export type YearMonth = string

export function currentYearMonth(): YearMonth {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export function isYearMonth(value: string | null): value is YearMonth {
  return !!value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

export function addMonths(month: YearMonth, delta: number): YearMonth {
  const [year, m] = month.split('-').map(Number)
  const date = new Date(year, m - 1 + delta, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

/** long: "Setembro de 2026" · short: "set/26" */
export function formatMonth(month: YearMonth, style: 'long' | 'short' = 'long') {
  const [year, m] = month.split('-').map(Number)
  const date = new Date(year, m - 1, 1)
  if (style === 'short') {
    const name = date.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '')
    return `${name}/${String(year).slice(-2)}`
  }
  const label = date.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
  return label.charAt(0).toUpperCase() + label.slice(1)
}

/** "2026-09-15" → "15/09/2026", sem fuso. */
export function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

/** Instante ISO → "há 5 minutos" ("há menos de um minuto" no início). */
export function formatRelative(iso: string) {
  return formatDistanceToNow(new Date(iso), { locale: ptBR, addSuffix: true })
}

export function todayIso() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

/** Hoje, se for o mês atual; senão dia 1. */
export function defaultDateInMonth(month: YearMonth) {
  return month === currentYearMonth() ? todayIso() : `${month}-01`
}

/** Aceita "1.234,56", "1.500" ou "1234.56"; NaN se inválido. */
export function parseAmount(raw: string): number {
  const value = raw.trim().replace(/\s|R\$/g, '')
  if (!value) return Number.NaN
  const thousandsOnly = /^\d{1,3}(\.\d{3})+$/.test(value)
  const normalized = value.includes(',') || thousandsOnly ? value.replace(/\./g, '').replace(',', '.') : value
  return /^\d+(\.\d{1,2})?$/.test(normalized) ? Number(normalized) : Number.NaN
}

/** 1234.5 → "1234,50" */
export function amountToInput(value: number) {
  return value.toFixed(2).replace('.', ',')
}
