import type { ReactNode } from 'react'
import { Area, AreaChart, CartesianGrid, Line, LineChart, ReferenceLine, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { cn } from '@/lib/utils'
import type { CompareItem, SimulationPoint } from '../types'

type TooltipProps = { active?: boolean; payload?: ReadonlyArray<{ payload?: unknown }> }

function Box({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="grid min-w-52 gap-1.5 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
      <p className="font-medium">{title}</p>
      {children}
    </div>
  )
}

function Row({ label, value, color, dashed, negative }: { label: string; value: number; color?: string; dashed?: boolean; negative?: boolean }) {
  const { formatCurrency } = useMoney()
  return (
    <div className="flex items-center gap-2">
      {color
        ? <span className={cn('h-0.5 w-2.5 shrink-0', dashed && 'border-t-2 border-dashed bg-transparent')} style={dashed ? { borderColor: color } : { background: color }} />
        : <span className="w-2.5" />}
      <span className="text-muted-foreground">{label}</span>
      <span className={cn('ml-auto font-medium tabular-nums', negative && value < 0 && 'text-destructive')}>{formatCurrency(value)}</span>
    </div>
  )
}

const balanceConfig = {
  simulated: { label: 'Simulado', color: 'var(--data)' },
  current: { label: 'Sem mudanças', color: 'var(--muted-foreground)' },
} satisfies ChartConfig

/** Saldo acumulado sem mudanças x simulado (saldo + investimento líquido). */
export function BalanceChart({ points, invest }: { points: SimulationPoint[]; invest: boolean }) {
  const { formatCompactCurrency } = useMoney()
  const data = points.map((p) => ({ ...p, label: formatMonth(p.month, 'short') }))
  return (
    <ChartContainer config={balanceConfig} className="aspect-auto h-72 w-full">
      <LineChart data={data} margin={{ top: 8, left: 4, right: 8 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={24} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ReferenceLine y={0} stroke="var(--destructive)" strokeDasharray="4 4" />
        <ChartTooltip
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <Box title={formatMonth(row.month)}>
                <Row color="var(--data)" label="Simulado" value={row.simulated} negative />
                {invest && <Row label="Em conta" value={row.cash} negative />}
                {invest && <Row label="Investido (líquido)" value={row.invested} />}
                <Row color="var(--muted-foreground)" dashed label="Sem mudanças" value={row.current} negative />
              </Box>
            )
          }}
        />
        <ChartLegend content={<ChartLegendContent />} />
        <Line dataKey="current" stroke="var(--color-current)" strokeDasharray="5 4" strokeWidth={1.5} dot={false} />
        <Line dataKey="simulated" stroke="var(--color-simulated)" strokeWidth={2} dot={false} />
      </LineChart>
    </ChartContainer>
  )
}

const investConfig = {
  contributed: { label: 'Aportado', color: 'var(--data)' },
  interest: { label: 'Rendimento', color: 'var(--income)' },
} satisfies ChartConfig

/** Quanto foi colocado e quanto rendeu (bruto), empilhados. */
export function InvestmentChart({ points }: { points: SimulationPoint[] }) {
  const { formatCompactCurrency } = useMoney()
  const data = points.map((p) => ({ ...p, label: formatMonth(p.month, 'short') }))
  return (
    <ChartContainer config={investConfig} className="aspect-auto h-64 w-full">
      <AreaChart data={data} margin={{ top: 8, left: 4, right: 8 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={24} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ChartTooltip
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <Box title={formatMonth(row.month)}>
                <Row color="var(--data)" label="Aportado" value={row.contributed} />
                <Row color="var(--income)" label="Rendimento" value={row.interest} />
                {row.tax > 0 && <Row label="IR se resgatar" value={-row.tax} />}
                <div className="my-0.5 border-t" />
                <Row label="Total líquido" value={row.invested} />
              </Box>
            )
          }}
        />
        <ChartLegend content={<ChartLegendContent />} />
        <Area dataKey="contributed" stackId="a" type="monotone" stroke="var(--color-contributed)" fill="var(--color-contributed)"
          fillOpacity={0.25} strokeWidth={2} />
        <Area dataKey="interest" stackId="a" type="monotone" stroke="var(--color-interest)" fill="var(--color-interest)"
          fillOpacity={0.3} strokeWidth={2} />
      </AreaChart>
    </ChartContainer>
  )
}

/** Mesma paleta categórica validada do gráfico de bancos; a cor segue a posição escolhida, não o valor. */
const COMPARE_COLORS = [
  { light: '#2a78d6', dark: '#3987e5' },
  { light: '#eb6834', dark: '#d95926' },
  { light: '#1baf7a', dark: '#199e70' },
]

/** Saldo simulado de 2 ou 3 simulações salvas, mês a mês. */
export function CompareChart({ items }: { items: CompareItem[] }) {
  const { formatCompactCurrency } = useMoney()
  const config: ChartConfig = Object.fromEntries(items.map((item, i) => [`s${i}`, { label: item.name, theme: COMPARE_COLORS[i] }]))
  const longest = items.reduce((max, item) => (item.points.length > max.points.length ? item : max), items[0])
  const data = longest.points.map((p, t) => ({
    month: p.month,
    label: formatMonth(p.month, 'short'),
    ...Object.fromEntries(items.map((item, i) => [`s${i}`, item.points[t]?.simulated ?? null])),
  }))
  return (
    <ChartContainer config={config} className="aspect-auto h-72 w-full">
      <LineChart data={data} margin={{ top: 8, left: 4, right: 8 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={24} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ReferenceLine y={0} stroke="var(--destructive)" strokeDasharray="4 4" />
        <ChartTooltip
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as Record<string, number | string | null>
            return (
              <Box title={formatMonth(String(row.month))}>
                {items.map((item, i) => row[`s${i}`] != null && (
                  <Row key={item.id} color={`var(--color-s${i})`} label={item.name} value={Number(row[`s${i}`])} negative />
                ))}
              </Box>
            )
          }}
        />
        <ChartLegend content={<ChartLegendContent />} />
        {items.map((item, i) => (
          <Line key={item.id} dataKey={`s${i}`} stroke={`var(--color-s${i})`} strokeWidth={2} dot={false} connectNulls={false} />
        ))}
      </LineChart>
    </ChartContainer>
  )
}
