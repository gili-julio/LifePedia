import type { ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, LabelList, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatCompactCurrency, formatCurrency, formatMonth } from '@/lib/format'
import type { MonthlySummary, MonthPoint } from '../types'

/** Ganhos (azul) x despesas (laranja): par validado para daltonismo. */
const config = {
  income: { label: 'Ganhos', color: 'var(--income)' },
  expense: { label: 'Despesas', color: 'var(--expense)' },
} satisfies ChartConfig

type TooltipRow = { dataKey?: unknown; value?: unknown; color?: string; payload?: unknown }
type TooltipProps = { active?: boolean; payload?: ReadonlyArray<TooltipRow>; label?: unknown }

function TooltipBox({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <div className="grid min-w-44 gap-1.5 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
      <p className="font-medium">{title}</p>
      {children}
    </div>
  )
}

function TooltipLine({ color, label, value, muted }: { color?: string; label: string; value: number; muted?: boolean }) {
  return (
    <div className="flex items-center gap-2">
      {color ? <span className="size-2.5 shrink-0 rounded-[2px]" style={{ background: color }} /> : <span className="w-2.5" />}
      <span className={muted ? 'text-muted-foreground' : undefined}>{label}</span>
      <span className="ml-auto font-medium tabular-nums">{formatCurrency(value)}</span>
    </div>
  )
}

/** Fixos x ocasionais do mês. */
export function FixedVsOccasionalChart({ summary }: { summary: MonthlySummary }) {
  const data = [
    { group: 'Fixos', income: summary.fixed.income, expense: summary.fixed.expense, balance: summary.fixed.balance },
    { group: 'Ocasionais', income: summary.occasional.income, expense: summary.occasional.expense, balance: summary.occasional.balance },
  ]

  return (
    <ChartContainer config={config} className="aspect-auto h-64 w-full">
      <BarChart data={data} margin={{ top: 24, left: 4, right: 4 }} barGap={2} barCategoryGap="28%">
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="group" tickLine={false} axisLine={false} tickMargin={8} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ChartTooltip
          cursor={{ fillOpacity: 0.4 }}
          content={(props) => {
            const { active, payload, label } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <TooltipBox title={label as string}>
                <TooltipLine color="var(--income)" label="Ganhos" value={row.income} />
                <TooltipLine color="var(--expense)" label="Despesas" value={row.expense} />
                <div className="my-0.5 border-t" />
                <TooltipLine label="Saldo" value={row.balance} muted />
              </TooltipBox>
            )
          }}
        />
        <ChartLegend content={<ChartLegendContent />} />
        <Bar dataKey="income" fill="var(--color-income)" radius={[4, 4, 0, 0]} maxBarSize={56}>
          <LabelList dataKey="income" position="top" className="fill-foreground" fontSize={11}
            formatter={(v: unknown) => (Number(v) ? formatCompactCurrency(Number(v)) : '')} />
        </Bar>
        <Bar dataKey="expense" fill="var(--color-expense)" radius={[4, 4, 0, 0]} maxBarSize={56}>
          <LabelList dataKey="expense" position="top" className="fill-foreground" fontSize={11}
            formatter={(v: unknown) => (Number(v) ? formatCompactCurrency(Number(v)) : '')} />
        </Bar>
      </BarChart>
    </ChartContainer>
  )
}

/** Ganhos x despesas por mês. */
const investedConfig = { invested: { label: 'Investido', color: 'var(--data)' } } satisfies ChartConfig

/** Investido por mês (despesas em categoria de investimento), fora das despesas. */
export function InvestedChart({ points }: { points: MonthPoint[] }) {
  const data = points.map((p) => ({ ...p, label: formatMonth(p.month, 'short') }))
  return (
    <ChartContainer config={investedConfig} className="aspect-auto h-56 w-full">
      <BarChart data={data} margin={{ top: 8, left: 4, right: 4 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ChartTooltip
          cursor={{ fillOpacity: 0.4 }}
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <TooltipBox title={formatMonth(row.month)}>
                <TooltipLine color="var(--data)" label="Investido" value={row.invested} />
              </TooltipBox>
            )
          }}
        />
        <Bar dataKey="invested" fill="var(--color-invested)" radius={[4, 4, 0, 0]} maxBarSize={32} />
      </BarChart>
    </ChartContainer>
  )
}

export function HistoryChart({ points }: { points: MonthPoint[] }) {
  const data = points.map((p) => ({
    ...p,
    label: formatMonth(p.month, 'short'),
    income: p.fixedIncome + p.occasionalIncome,
    expense: p.fixedExpense + p.occasionalExpense,
  }))

  return (
    <ChartContainer config={config} className="aspect-auto h-64 w-full">
      <BarChart data={data} margin={{ top: 8, left: 4, right: 4 }} barGap={2}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ChartTooltip
          cursor={{ fillOpacity: 0.4 }}
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <TooltipBox title={formatMonth(row.month)}>
                <TooltipLine color="var(--income)" label="Ganhos" value={row.income} />
                <TooltipLine label="fixos" value={row.fixedIncome} muted />
                <TooltipLine label="ocasionais" value={row.occasionalIncome} muted />
                <TooltipLine color="var(--expense)" label="Despesas" value={row.expense} />
                <TooltipLine label="fixas" value={row.fixedExpense} muted />
                <TooltipLine label="ocasionais" value={row.occasionalExpense} muted />
                {row.invested > 0 && <TooltipLine color="var(--data)" label="Investido" value={row.invested} />}
                <div className="my-0.5 border-t" />
                <TooltipLine label="Saldo" value={row.balance} />
              </TooltipBox>
            )
          }}
        />
        <ChartLegend content={<ChartLegendContent />} />
        <Bar dataKey="income" fill="var(--color-income)" radius={[4, 4, 0, 0]} maxBarSize={32} />
        <Bar dataKey="expense" fill="var(--color-expense)" radius={[4, 4, 0, 0]} maxBarSize={32} />
      </BarChart>
    </ChartContainer>
  )
}
