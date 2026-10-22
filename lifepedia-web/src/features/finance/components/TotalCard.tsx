import { ArrowDownRightIcon, ArrowUpRightIcon, ScaleIcon } from 'lucide-react'
import { StatCard } from '@/components/StatCard'
import type { EntryType } from '../types'
import { Money } from './Money'

const KIND = {
  INCOME: { tone: 'income', icon: ArrowUpRightIcon },
  EXPENSE: { tone: 'expense', icon: ArrowDownRightIcon },
  BALANCE: { tone: 'data', icon: ScaleIcon },
} as const

/** Total calculado pela API (`/summary`): ganhos, despesas ou saldo; sem valor, mostra carregando. */
export function TotalCard({ label, value, kind }: { label: string; value?: number; kind: EntryType | 'BALANCE' }) {
  const { tone, icon } = KIND[kind]
  return (
    <StatCard label={label} tone={tone} icon={icon}>
      {value === undefined ? undefined
        : kind === 'BALANCE' ? <Money value={value} signed /> : <Money value={value} type={kind} />}
    </StatCard>
  )
}
