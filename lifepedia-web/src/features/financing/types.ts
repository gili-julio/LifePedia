import type { UserSummary } from '@/features/groups/types'
import type { YearMonth } from '@/lib/format'

/** Espelha `FinancingKind`, `ItemCondition`, `CorrectionIndex`, `FinancingMath.AmortizationSystem/ExtraMode`. */
export type FinancingKind = 'HOUSE' | 'APARTMENT' | 'CAR' | 'MOTORCYCLE' | 'OTHER'
export type ItemCondition = 'NEW' | 'USED'
export type CorrectionIndex = 'NONE' | 'TR' | 'IPCA' | 'INCC'
export type AmortizationSystem = 'SAC' | 'PRICE'
export type ExtraMode = 'TERM' | 'PAYMENT'
/** Espelha `FinancingDtos.Scenario` e `CostWhen`; `FinancingCost.Frequency`. */
export type Scenario = 'LOW' | 'MEDIAN' | 'HIGH' | 'CUSTOM'
export type CostWhen = 'UPFRONT' | 'FINANCED' | 'INSTALLMENT' | 'YEARLY' | 'MONTHLY'
export type Frequency = 'MONTHLY' | 'YEARLY'
export type Modality = 'VEHICLE' | 'HOUSING' | 'OTHER'

export type SimulationRequest = {
  kind: FinancingKind
  condition: ItemCondition
  price: number
  downPayment: number
  fgts?: number | null
  /** Mês da entrada; a 1ª parcela vence no seguinte */
  startMonth?: YearMonth | null
  months?: number | null
  income?: number | null
  /** % ao mês */
  customRate?: number | null
  /** Valor editado de cada gasto padrão (chave → valor) */
  costs?: Record<string, number>
  scenario?: Scenario | null
  system?: AmortizationSystem | null
}

export type RateSource = { modality: Modality; label: string; period: string | null; banks: number; fallback: boolean }

export type SystemResult = {
  firstPayment: number
  lastPayment: number
  totalPaid: number
  totalInterest: number
  cetMonthly: number | null
  cetYearly: number | null
}

export type ScenarioResult = { scenario: Scenario; monthlyRate: number; yearlyRate: number; sac: SystemResult; price: SystemResult }

export type TermRow = { months: number; priceFirst: number; priceInterest: number; sacFirst: number; sacLast: number; sacInterest: number }

export type CostItem = { key: string; name: string; amount: number; when: CostWhen; bank: boolean; hint: string | null; edited: boolean }

export type CostTotals = {
  upfront: number
  upfrontCosts: number
  financedCosts: number
  installmentFee: number
  yearly: number
  monthly: number
}

export type IncomeCheck = { income: number; payment: number; percent: number; limit: number; maxPayment: number; maxPrice: number }

export type BankOption = { position: number; bank: string; monthlyRate: number; yearlyRate: number; payment: number }

/** Parcela: `payment` já tem seguro e tarifa (`charges`). */
export type ScheduleRow = {
  number: number
  month: YearMonth
  payment: number
  interest: number
  amortization: number
  charges: number
  extra: number
  balance: number
}

export type SimulationResponse = {
  rates: RateSource
  base: number
  financed: number
  months: number
  firstMonth: YearMonth
  correction: { index: CorrectionIndex; monthly: number } | null
  scenarios: ScenarioResult[]
  terms: TermRow[]
  costs: CostItem[]
  costTotals: CostTotals
  income: IncomeCheck | null
  banks: BankOption[]
  scenario: Scenario
  system: AmortizationSystem
  schedule: ScheduleRow[]
  warnings: string[]
}

export type RateSuggestion = { source: RateSource; low: number; median: number; high: number; index: CorrectionIndex }

export type SavedSimulation = {
  id: string
  name: string
  input: SimulationRequest
  createdBy: UserSummary | null
  createdAt: string
  canDelete: boolean
}

export type NextPayment = { number: number; month: YearMonth; date: string; amount: number }

export type Progress = {
  paid: number
  remaining: number
  totalMonths: number
  lastMonth: YearMonth | null
  balance: number
  percent: number
  interestPaid: number
  interestRemaining: number
  next: NextPayment | null
  payoff: number
  payoffSavings: number
}

export type FinancingMember = {
  user: UserSummary
  share: number
  nextAmount: number
  /** Só no próprio membro */
  recurringEntryId: string | null
  fixed: boolean
  me: boolean
}

export type ExtraPayment = { id: string; month: YearMonth; amount: number; mode: ExtraMode }

export type FinancingCost = {
  id: string
  name: string
  amount: number
  frequency: Frequency
  dueMonth: number | null
  dueDay: number
  nextDate: string
}

export type Financing = {
  id: string
  groupId: string | null
  name: string
  kind: FinancingKind
  condition: ItemCondition
  price: number | null
  downPayment: number | null
  fgts: number | null
  financed: number
  monthlyRate: number
  yearlyRate: number
  system: AmortizationSystem
  months: number
  firstMonth: YearMonth
  dueDay: number
  insuranceRate: number
  monthlyFee: number
  index: CorrectionIndex
  indexProjection: number | null
  bank: string | null
  reminderDays: number | null
  notes: string | null
  progress: Progress
  members: FinancingMember[]
  extras: ExtraPayment[]
  costs: FinancingCost[]
  schedule: ScheduleRow[]
  createdBy: UserSummary | null
  canDelete: boolean
}

export type FinancingSummary = {
  id: string
  groupId: string | null
  groupName: string | null
  name: string
  kind: FinancingKind
  financed: number
  progress: Progress
  myShare: number | null
}

export type FinancingRequest = {
  name: string
  kind: FinancingKind
  condition: ItemCondition
  price: number | null
  downPayment: number | null
  fgts: number | null
  financed: number
  monthlyRate: number
  system: AmortizationSystem
  months: number
  firstMonth: YearMonth
  dueDay: number
  insuranceRate: number | null
  monthlyFee: number | null
  index: CorrectionIndex
  bank: string | null
  reminderDays: number | null
  notes: string | null
  shares: { userId: string; share: number }[] | null
}

export type ExtraRequest = { month: YearMonth; amount: number; mode: ExtraMode }

export type ExtraPreview = {
  monthsBefore: number
  monthsAfter: number
  lastMonthBefore: YearMonth | null
  lastMonthAfter: YearMonth | null
  nextPaymentBefore: number
  nextPaymentAfter: number
  interestSaved: number
  totalSaved: number
}

export type CostRequest = { name: string; amount: number; frequency: Frequency; dueMonth: number | null; dueDay: number }
