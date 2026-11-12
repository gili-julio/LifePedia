import { AlertTriangleIcon, PiggyBankIcon, TrendingDownIcon, TrendingUpIcon, WalletIcon } from 'lucide-react'
import { StatCard } from '@/components/StatCard'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import type { SimulationResult } from '../types'
import { BalanceChart, InvestmentChart } from './charts'

const pct = (value: number) => value.toLocaleString('pt-BR', { maximumFractionDigits: 2 })

/** Cards de resumo, saldo atual x simulado e aportado x rendimento. */
export function SimulationResults({ data, invest }: { data: SimulationResult; invest: boolean }) {
  const { formatCurrency } = useMoney()
  const s = data.summary
  const last = data.points.at(-1)
  const end = last ? formatMonth(last.month, 'short') : ''
  const signed = (v: number) => `${v > 0 ? '+' : v < 0 ? '−' : ''}${formatCurrency(Math.abs(v))}`
  return (
    <div className="space-y-4">
      {(data.real || data.rates.fallback) && (
        <p className="text-xs text-muted-foreground">
          {data.real && `Valores em reais de hoje: descontada a inflação projetada (IPCA de ${pct(data.rates.ipca)}% ao ano). `}
          {data.rates.fallback && 'Sem dados recentes do Banco Central: usamos taxas padrão.'}
        </p>
      )}
      <div className="grid gap-3 @lg:grid-cols-2 @3xl:grid-cols-4">
        <StatCard label={`Saldo em ${end}`} tone="data" icon={WalletIcon} hint={`Sem mudanças: ${formatCurrency(s.finalCurrent)}`}>
          <span className={s.finalSimulated < 0 ? 'text-destructive' : undefined}>{formatCurrency(s.finalSimulated)}</span>
        </StatCard>
        <StatCard label="Diferença" tone={s.difference < 0 ? 'expense' : 'income'} icon={s.difference < 0 ? TrendingDownIcon : TrendingUpIcon}
          hint="Simulado menos sem mudanças">
          {signed(s.difference)}
        </StatCard>
        {invest && (
          <StatCard label="Rendimento" tone="income" icon={PiggyBankIcon}
            hint={`Aportado ${formatCurrency(s.contributed)}${s.tax > 0 ? ` · IR ${formatCurrency(s.tax)}` : ''}${s.yearlyRate != null ? ` · ${pct(s.yearlyRate)}% ao ano` : ''}`}>
            {signed(s.interest)}
          </StatCard>
        )}
        <StatCard label="Saldo negativo" tone={s.firstNegativeSimulated ? 'expense' : 'data'} icon={AlertTriangleIcon}
          hint={s.firstNegativeCurrent ? `Sem mudanças: a partir de ${formatMonth(s.firstNegativeCurrent, 'short')}` : 'Sem mudanças: nunca'}>
          {s.firstNegativeSimulated ? formatMonth(s.firstNegativeSimulated, 'short') : 'Nunca'}
        </StatCard>
      </div>
      <Card>
        <CardHeader><CardTitle className="text-base">Saldo: sem mudanças x simulado</CardTitle></CardHeader>
        <CardContent><BalanceChart points={data.points} invest={invest} /></CardContent>
      </Card>
      {invest && (
        <Card>
          <CardHeader><CardTitle className="text-base">Aportado x rendimento</CardTitle></CardHeader>
          <CardContent>
            <InvestmentChart points={data.points} />
            <p className="mt-2 text-xs text-muted-foreground">
              Líquido em {end}: {formatCurrency(s.invested)}{s.tax > 0 ? ' (descontado o IR se resgatar tudo)' : ''}.
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
