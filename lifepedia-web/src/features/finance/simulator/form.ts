import { addMonths, amountToInput, currentYearMonth, parseAmount, type YearMonth } from '@/lib/format'
import type { EntryType } from '../types'
import type { SimulationChange, SimulationInput, Yield } from './types'

export type ChangeForm = {
  key: string
  name: string
  type: EntryType
  amount: string
  start: YearMonth
  end: YearMonth | ''
  once: boolean
}

/** Formulário do simulador (texto como digitado); vira `SimulationInput` por `toInput`. */
export type SimForm = {
  months: string
  startBalance: string
  useFixed: boolean
  averageMonths: SimulationInput['averageMonths']
  changes: ChangeForm[]
  invest: boolean
  initial: string
  monthly: string
  source: Yield
  rate: string
  tax: boolean
  fromBalance: boolean
}

export const HORIZONS = [
  { months: 6, label: '6 meses' },
  { months: 12, label: '1 ano' },
  { months: 24, label: '2 anos' },
  { months: 60, label: '5 anos' },
  { months: 120, label: '10 anos' },
  { months: 240, label: '20 anos' },
]

export const YIELDS: Record<Yield, { label: string; rate: 'cdi' | 'selic' | 'ipca' | null; rateLabel: string; defaultRate: string }> = {
  CDI: { label: '% do CDI', rate: 'cdi', rateLabel: '% do CDI', defaultRate: '100' },
  SELIC: { label: 'Tesouro Selic', rate: 'selic', rateLabel: '% da Selic', defaultRate: '100' },
  IPCA: { label: 'IPCA +', rate: 'ipca', rateLabel: 'Taxa ao ano acima do IPCA (%)', defaultRate: '6' },
  FIXED: { label: 'Prefixado', rate: null, rateLabel: 'Taxa ao ano (%)', defaultRate: '12' },
  SAVINGS: { label: 'Poupança', rate: null, rateLabel: '', defaultRate: '' },
}

export const EMPTY_FORM: SimForm = {
  months: '12',
  startBalance: '',
  useFixed: true,
  averageMonths: 3,
  changes: [],
  invest: false,
  initial: '',
  monthly: '',
  source: 'CDI',
  rate: '100',
  tax: true,
  fromBalance: true,
}

export function newChange(type: EntryType): ChangeForm {
  return { key: crypto.randomUUID(), name: '', type, amount: '', start: addMonths(currentYearMonth(), 1), end: '', once: false }
}

/** Aceita "-1.234,56" (saldo negativo). */
function signedAmount(raw: string): number {
  const text = raw.trim()
  if (!text) return 0
  const negative = text.startsWith('-')
  const value = parseAmount(negative ? text.slice(1) : text)
  return negative ? -value : value
}

const amountOr0 = (raw: string) => (raw.trim() ? parseAmount(raw) : 0)

/** Nulo se o prazo ou um valor for inválido; linhas sem nome ou valor ficam de fora. */
export function toInput(form: SimForm): SimulationInput | null {
  const months = Number(form.months)
  const startBalance = signedAmount(form.startBalance)
  if (!Number.isInteger(months) || months < 1 || months > 600 || Number.isNaN(startBalance)) return null
  const changes: SimulationChange[] = form.changes
    .map((c) => ({ c, amount: parseAmount(c.amount) }))
    .filter(({ c, amount }) => c.name.trim() && amount > 0)
    .map(({ c, amount }) => ({ name: c.name.trim(), type: c.type, amount, start: c.start, end: c.once || !c.end ? null : c.end, once: c.once }))
  let investment: SimulationInput['investment'] = null
  if (form.invest) {
    const initial = amountOr0(form.initial)
    const monthly = amountOr0(form.monthly)
    const rate = form.source === 'SAVINGS' ? null : Number(form.rate.replace(',', '.'))
    if (Number.isNaN(initial) || Number.isNaN(monthly) || (rate !== null && (Number.isNaN(rate) || rate < 0 || rate > 300))) return null
    investment = { initial, monthly, source: form.source, rate, tax: form.tax, fromBalance: form.fromBalance }
  }
  return { months, startBalance, useFixed: form.useFixed, averageMonths: form.averageMonths, changes, investment }
}

export function fromInput(input: SimulationInput): SimForm {
  const inv = input.investment
  return {
    months: String(input.months),
    startBalance: input.startBalance ? (input.startBalance < 0 ? '-' : '') + amountToInput(Math.abs(input.startBalance)) : '',
    useFixed: input.useFixed,
    averageMonths: input.averageMonths,
    changes: (input.changes ?? []).map((c) => ({
      key: crypto.randomUUID(), name: c.name, type: c.type, amount: amountToInput(c.amount), start: c.start, end: c.end ?? '', once: c.once,
    })),
    invest: !!inv,
    initial: inv?.initial ? amountToInput(inv.initial) : '',
    monthly: inv?.monthly ? amountToInput(inv.monthly) : '',
    source: inv?.source ?? 'CDI',
    rate: inv?.rate != null ? String(inv.rate).replace('.', ',') : YIELDS[inv?.source ?? 'CDI'].defaultRate,
    tax: inv?.tax ?? true,
    fromBalance: inv?.fromBalance ?? true,
  }
}
