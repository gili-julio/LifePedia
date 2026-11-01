import type { ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, LabelList, Line, LineChart, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import type { BankLine, BankOverview, MonthlySummary, MonthPoint } from '../types'

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
  const { formatCurrency } = useMoney()
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
  const { formatCompactCurrency } = useMoney()
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
  const { formatCompactCurrency } = useMoney()
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

/** Paleta categórica validada para daltonismo; a ordem é a das chaves, não a dos valores, para a cor seguir o banco. */
const BANK_COLORS = [
  { light: '#2a78d6', dark: '#3987e5' },
  { light: '#eb6834', dark: '#d95926' },
  { light: '#1baf7a', dark: '#199e70' },
  { light: '#eda100', dark: '#c98500' },
  { light: '#e87ba4', dark: '#d55181' },
]
const REST_COLOR = 'var(--muted-foreground)'

/** Gastos por mês nos 5 bancos com mais gastos; o resto soma em "Outros". */
export function BankHistoryChart({ overview }: { overview: BankOverview }) {
  const { formatCompactCurrency } = useMoney()
  const sum = (line: BankLine) => line.history.reduce((a, b) => a + b, 0)
  const lines = overview.banks.filter((b) => sum(b) > 0)
  if (!lines.length) return <p className="py-6 text-center text-sm text-muted-foreground">Nenhum gasto nestes meses.</p>

  const top = lines.filter((b) => b.code).sort((a, b) => sum(b) - sum(a)).slice(0, BANK_COLORS.length)
    .sort((a, b) => a.key.localeCompare(b.key))
  const rest = lines.filter((b) => !top.includes(b))
  const chartConfig: ChartConfig = Object.fromEntries(top.map((b, i) => [`s${i}`, { label: b.name, theme: BANK_COLORS[i] }]))
  if (rest.length) chartConfig.rest = { label: 'Outros', color: REST_COLOR }
  const data = overview.months.map((month, m) => ({
    month,
    label: formatMonth(month, 'short'),
    ...Object.fromEntries(top.map((b, i) => [`s${i}`, b.history[m]])),
    ...(rest.length && { rest: rest.reduce((total, b) => total + b.history[m], 0) }),
  }))
  const series = Object.keys(chartConfig)

  return (
    <ChartContainer config={chartConfig} className="aspect-auto h-64 w-full">
      <LineChart data={data} margin={{ top: 8, left: 4, right: 8 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ChartTooltip
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as Record<string, number | string>
            const rows = series
              .map((key) => ({ key, value: Number(row[key] ?? 0) }))
              .sort((a, b) => b.value - a.value)
            return (
              <TooltipBox title={formatMonth(String(row.month))}>
                {rows.map(({ key, value }) => (
                  <TooltipLine key={key} color={key === 'rest' ? REST_COLOR : `var(--color-${key})`}
                    label={String(chartConfig[key].label)} value={value} />
                ))}
              </TooltipBox>
            )
          }}
        />
        <ChartLegend content={<ChartLegendContent className="flex-wrap" />} />
        {series.map((key) => (
          <Line key={key} dataKey={key} type="monotone" stroke={`var(--color-${key})`} strokeWidth={2}
            strokeDasharray={key === 'rest' ? '4 3' : undefined} dot={{ r: 4, fill: `var(--color-${key})`, strokeWidth: 0 }} activeDot={{ r: 5 }} isAnimationActive={false} />
        ))}
      </LineChart>
    </ChartContainer>
  )
}

export function HistoryChart({ points }: { points: MonthPoint[] }) {
  const { formatCompactCurrency } = useMoney()
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
