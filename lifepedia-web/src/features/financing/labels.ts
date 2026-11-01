import { Building2Icon, CarIcon, HouseIcon, MotorbikeIcon, PackageIcon, type LucideIcon } from 'lucide-react'
import { formatMonth, type YearMonth } from '@/lib/format'
import type { AmortizationSystem, CorrectionIndex, CostWhen, FinancingKind, ItemCondition, Scenario, ScheduleRow } from './types'

export const KINDS: { kind: FinancingKind; label: string; icon: LucideIcon }[] = [
  { kind: 'HOUSE', label: 'Casa', icon: HouseIcon },
  { kind: 'APARTMENT', label: 'Apartamento', icon: Building2Icon },
  { kind: 'CAR', label: 'Carro', icon: CarIcon },
  { kind: 'MOTORCYCLE', label: 'Moto', icon: MotorbikeIcon },
  { kind: 'OTHER', label: 'Outro bem', icon: PackageIcon },
]

export const kindInfo = (kind: FinancingKind) => KINDS.find((k) => k.kind === kind) ?? KINDS[4]

export const isHousing = (kind: FinancingKind) => kind === 'HOUSE' || kind === 'APARTMENT'
export const isVehicle = (kind: FinancingKind) => kind === 'CAR' || kind === 'MOTORCYCLE'

/** Espelha `FinancingKind` da API (prazo padrão e máximo). */
export const MONTHS_LIMIT: Record<FinancingKind, { default: number; max: number }> = {
  HOUSE: { default: 360, max: 420 },
  APARTMENT: { default: 360, max: 420 },
  CAR: { default: 48, max: 72 },
  MOTORCYCLE: { default: 36, max: 60 },
  OTHER: { default: 24, max: 60 },
}

export function conditionLabel(kind: FinancingKind, condition: ItemCondition) {
  if (isHousing(kind)) return condition === 'NEW' ? 'Novo' : 'Usado'
  if (isVehicle(kind)) return condition === 'NEW' ? '0 km' : 'Seminovo'
  return condition === 'NEW' ? 'Novo' : 'Usado'
}

export const SCENARIO_LABEL: Record<Scenario, string> = {
  LOW: 'Taxa boa',
  MEDIAN: 'Taxa média',
  HIGH: 'Taxa alta',
  CUSTOM: 'Sua taxa',
}

export const SCENARIO_HINT: Record<Scenario, string> = {
  LOW: 'Entre os bancos mais baratos',
  MEDIAN: 'Meio da lista de bancos',
  HIGH: 'Entre os bancos mais caros',
  CUSTOM: 'A proposta que você informou',
}

export const SYSTEM_LABEL: Record<AmortizationSystem, string> = { SAC: 'SAC', PRICE: 'Price' }

export const SYSTEM_HINT: Record<AmortizationSystem, string> = {
  SAC: 'Parcela começa maior e diminui todo mês; paga menos juros no total.',
  PRICE: 'Parcela igual do começo ao fim (sem correção); paga mais juros no total.',
}

export const INDEX_LABEL: Record<CorrectionIndex, string> = {
  NONE: 'Sem correção',
  TR: 'TR',
  IPCA: 'IPCA',
  INCC: 'INCC',
}

export const WHEN_LABEL: Record<CostWhen, string> = {
  UPFRONT: 'Pago na compra',
  FINANCED: 'Somado ao financiamento',
  INSTALLMENT: 'Dentro da parcela, todo mês',
  YEARLY: 'Todo ano, fora da parcela',
  MONTHLY: 'Todo mês, fora da parcela',
}

export const MONTH_NAMES = Array.from({ length: 12 }, (_, i) =>
  new Date(2000, i, 1).toLocaleDateString('pt-BR', { month: 'long' }))

/** "% ao mês" com vírgula. */
export const formatRate = (value: number, digits = 2) =>
  `${value.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits })}%`

export function formatMonths(months: number) {
  const years = Math.floor(months / 12)
  const rest = months % 12
  const parts = [years ? `${years} ${years === 1 ? 'ano' : 'anos'}` : '', rest ? `${rest} ${rest === 1 ? 'mês' : 'meses'}` : '']
  return `${months} ${months === 1 ? 'mês' : 'meses'}${years ? ` (${parts.filter(Boolean).join(' e ')})` : ''}`
}

export function rangeLabel(first: YearMonth, last: YearMonth | null) {
  return last ? `${formatMonth(first, 'short')} → ${formatMonth(last, 'short')}` : formatMonth(first, 'short')
}

/** Taxa ao mês a partir da anual (%). */
export function monthlyFromYearly(yearly: number) {
  return (Math.pow(1 + yearly / 100, 1 / 12) - 1) * 100
}

/** Tabela de parcelas em CSV (separador `;`, decimais com vírgula, como o Excel em pt-BR espera). */
export function scheduleCsv(rows: ScheduleRow[]) {
  const n = (v: number) => v.toFixed(2).replace('.', ',')
  const lines = ['Parcela;Mês;Parcela (R$);Juros;Amortização;Seguros e tarifas;Amortização extra;Saldo devedor']
  for (const r of rows) {
    lines.push([r.number, r.month, n(r.payment), n(r.interest), n(r.amortization), n(r.charges), n(r.extra), n(r.balance)].join(';'))
  }
  return `﻿${lines.join('\r\n')}`
}

export function downloadText(content: string, name: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}
