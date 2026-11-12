import type { YearMonth } from '@/lib/format'
import type { EntryType } from '../types'

/** Espelha `finance/simulation/SimulatorDtos`. */
export type Yield = 'CDI' | 'SELIC' | 'IPCA' | 'FIXED' | 'SAVINGS'

export type SimulationChange = {
  name: string
  type: EntryType
  amount: number
  start: YearMonth
  end?: YearMonth | null
  once: boolean
}

export type SimulationInvestment = {
  initial: number
  monthly: number
  source: Yield
  rate?: number | null
  tax: boolean
  fromBalance: boolean
}

export type SimulationInput = {
  months: number
  startBalance: number
  useFixed: boolean
  averageMonths: 0 | 3 | 6 | 12
  changes?: SimulationChange[]
  investment?: SimulationInvestment | null
}

export type SimulationPoint = {
  month: YearMonth
  current: number
  simulated: number
  cash: number
  contributed: number
  interest: number
  tax: number
  invested: number
}

export type SimulationSummary = {
  finalCurrent: number
  finalSimulated: number
  difference: number
  contributed: number
  interest: number
  tax: number
  invested: number
  firstNegativeCurrent: YearMonth | null
  firstNegativeSimulated: YearMonth | null
  monthlyRate: number | null
  yearlyRate: number | null
}

export type SimulationResult = {
  start: YearMonth
  real: boolean
  base: { fixedIncome: number; fixedExpense: number; occasionalAverage: number; averageBasis: YearMonth[]; monthly: number }
  rates: { cdi: number; selic: number; ipca: number; fallback: boolean }
  summary: SimulationSummary
  points: SimulationPoint[]
}

export type SavedFinanceSimulation = {
  id: string
  name: string
  input: SimulationInput
  createdAt: string
  updatedAt: string
}

export type CompareItem = { id: string; name: string; summary: SimulationSummary; points: SimulationPoint[] }
