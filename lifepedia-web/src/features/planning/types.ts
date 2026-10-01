import type { UserSummary } from '@/features/groups/types'
import type { YearMonth } from '@/lib/format'

/** Espelha `PlanItem.Kind`. */
export type PlanKind = 'PURCHASE' | 'INVESTMENT'

export type PlanItem = {
  id: string
  name: string
  kind: PlanKind
  amount: number
  targetMonth: YearMonth
  /** 1 = à vista; parcelas a partir do prazo */
  installments: number
  /** Prioridade: menor recebe o dinheiro primeiro */
  position: number
  active: boolean
  notes: string | null
  url: string | null
  linkTitle: string | null
  linkSite: string | null
  linkPrice: number | null
  /** De onde vieram preço/foto quando a loja do link não liberou (produto parecido) */
  referenceSite: string | null
  referenceTitle: string | null
  referenceUrl: string | null
  referencePrice: number | null
  /** Nula sem foto; traz `?v=` (versão) */
  imageUrl: string | null
  acquiredOn: string | null
  acquiredAmount: number | null
  createdBy: UserSummary
  /** Quem cadastrou ou admin */
  canEdit: boolean
}

export type ItemResult = {
  item: PlanItem
  /** Entra na simulação (ativo, ou no cenário que está sendo visto) */
  considered: boolean
  /** Quanto falta no prazo (0 = cabe); não considerado: quanto faltaria se entrasse */
  shortfall: number
  /** Primeiro mês em que caberia; nulo = não cabe nem 5 anos depois do período mostrado */
  earliestMonth: YearMonth | null
  /** À vista: quanto guardar por mês até o prazo */
  monthlySaving: number | null
  /** Parcelado: valor da parcela */
  installmentAmount: number | null
  /** Prazo já passou (simulado no mês atual) */
  overdue: boolean
}

export type AverageMonths = 0 | 3 | 6 | 12

export type PlanSettings = { reserve: number; averageMonths: AverageMonths; manualBalance: number | null }

/** Saldo previsto do primeiro mês e de onde vem. */
export type PlanBase = {
  fixedIncome: number
  fixedExpense: number
  /** Média de (ganhos − despesas) ocasionais */
  occasionalAverage: number
  averageMonths: AverageMonths
  /** Meses usados na média: os completos da janela com lançamentos (ou só o atual) */
  averageBasis: YearMonth[]
  manualBalance: number | null
  /** "E se" da tela (ganho extra − gasto extra) */
  extra: number
  monthly: number
}

export type PlanOutcome = {
  count: number
  total: number
  /** Maior falta no período (0 = tudo cabe) */
  shortfall: number
  lowestBalance: number
  lowestMonth: YearMonth
  endBalance: number
  endMonth: YearMonth
}

export type TimelinePoint = {
  month: YearMonth
  base: number
  planned: number
  withoutPlan: number
  /** Saldo acumulado no fim do mês, já descontadas as compras */
  balance: number
  /** Itens com prazo no mês */
  due: string[]
}

export type Simulation = {
  start: YearMonth
  scenarioId: string | null
  settings: PlanSettings
  base: PlanBase
  outcome: PlanOutcome
  timeline: TimelinePoint[]
  items: ItemResult[]
  acquired: PlanItem[]
}

export type Scenario = { id: string; name: string; itemIds: string[]; createdBy: UserSummary | null; canEdit: boolean }

/** `scenarioId` nulo = itens ativos agora. */
export type ScenarioComparison = { scenarioId: string | null; name: string; outcome: PlanOutcome }

/** Dados lidos do link (tudo opcional). */
export type LinkPreview = {
  url: string
  title: string | null
  /** Nome tirado do endereço (a loja não liberou a página) */
  titleFromUrl: boolean
  price: number | null
  currency: string | null
  site: string | null
  /** Foto já reduzida (data URL JPEG), enviada de volta ao salvar */
  imageData: string | null
  blocked: boolean
  /** Produto parecido em outro site, quando faltou preço ou foto */
  reference: Reference | null
}

/** Produto parecido achado em outro site (preço e foto de referência). */
export type Reference = {
  site: string
  title: string
  /** Página do produto (ou da busca) nesse site */
  url: string
  price: number
  /** Quantas lojas vendem; nulo = oferta de uma loja só */
  stores: number | null
  /** Loja do menor preço */
  store: string | null
  /** Foto (data URL); nula se a página do link já tinha foto */
  imageData: string | null
}

export type PlanItemRequest = {
  name: string
  kind: PlanKind
  amount: number
  targetMonth: YearMonth
  installments: number
  notes: string | null
  active?: boolean
  url: string | null
  linkTitle: string | null
  linkSite: string | null
  linkPrice: number | null
  referenceSite: string | null
  referenceTitle: string | null
  referenceUrl: string | null
  referencePrice: number | null
  /** Nova foto (data URL); ausente mantém a atual */
  imageData?: string | null
  removeImage?: boolean
}

export type AcquireRequest = { date: string; amount: number; launch: boolean; categoryId: string | null }

/** Simulação "e se" (só na tela). */
export type WhatIf = { extraIncome: number; extraExpense: number }
