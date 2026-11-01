import { amountToInput, currentYearMonth, parseAmount } from '@/lib/format'
import { isHousing, monthlyFromYearly } from './labels'
import type { FinancingKind, ItemCondition, SimulationRequest } from './types'

/** Campos do simulador como texto (convertidos no pedido). */
export type SimForm = {
  kind: FinancingKind
  condition: ItemCondition
  price: string
  downPayment: string
  fgts: string
  startMonth: string
  months: string
  income: string
  rate: string
  rateUnit: 'month' | 'year'
}

export const EMPTY_FORM: SimForm = {
  kind: 'CAR',
  condition: 'NEW',
  price: '',
  downPayment: '',
  fgts: '',
  startMonth: currentYearMonth(),
  months: '',
  income: '',
  rate: '',
  rateUnit: 'month',
}

const decimal = (v: string) => Number(v.trim().replace(',', '.'))

/** Pedido da simulação; nulo enquanto o preço não é válido. */
export function toRequest(form: SimForm): SimulationRequest | null {
  const price = parseAmount(form.price)
  if (!(price >= 1000)) return null
  const down = form.downPayment.trim() ? parseAmount(form.downPayment) : 0
  if (Number.isNaN(down)) return null
  const months = /^\d+$/.test(form.months) ? Number(form.months) : null
  const rate = /^\d+([.,]\d{1,4})?$/.test(form.rate.trim()) ? decimal(form.rate) : null
  const fgts = isHousing(form.kind) && form.fgts.trim() ? parseAmount(form.fgts) : null
  const income = form.income.trim() ? parseAmount(form.income) : null
  return {
    kind: form.kind,
    condition: form.condition,
    price,
    downPayment: down,
    fgts: fgts !== null && !Number.isNaN(fgts) ? fgts : null,
    startMonth: form.startMonth || null,
    months,
    income: income !== null && !Number.isNaN(income) ? income : null,
    customRate: rate === null ? null
      : Math.round((form.rateUnit === 'year' ? monthlyFromYearly(rate) : rate) * 10000) / 10000,
  }
}

export function fromRequest(input: SimulationRequest): SimForm {
  const money = (v: number | null | undefined) => (v ? amountToInput(v) : '')
  return {
    kind: input.kind,
    condition: input.condition,
    price: money(input.price),
    downPayment: money(input.downPayment),
    fgts: money(input.fgts),
    startMonth: input.startMonth ?? currentYearMonth(),
    months: input.months ? String(input.months) : '',
    income: money(input.income),
    rate: input.customRate ? String(input.customRate).replace('.', ',') : '',
    rateUnit: 'month',
  }
}
