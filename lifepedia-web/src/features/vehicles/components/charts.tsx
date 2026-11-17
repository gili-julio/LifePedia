import { Bar, BarChart, CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatDate, formatMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { GROUPS, formatKm, formatNumber } from '../labels'
import type { MonthStat, VehicleStats } from '../types'

type TooltipProps = { active?: boolean; payload?: ReadonlyArray<{ payload?: unknown }> }

const spendConfig = Object.fromEntries(GROUPS.map((g) => [g.key, { label: g.label, color: g.color }])) satisfies ChartConfig

/** Gasto por mês, empilhado por grupo (cor fixa por grupo, nunca pela posição do valor). */
export function SpendChart({ months }: { months: MonthStat[] }) {
  const { formatCurrency, formatCompactCurrency } = useMoney()
  const data = months.map((m) => ({ ...m, label: formatMonth(m.month, 'short') }))
  return (
    <ChartContainer config={spendConfig} className="aspect-auto h-64 w-full">
      <BarChart data={data} margin={{ top: 8, left: 4, right: 8 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={12} />
        <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        <ChartTooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }}
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <div className="grid min-w-48 gap-1.5 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
                <p className="font-medium">{formatMonth(row.month)}</p>
                {GROUPS.map((g) => (
                  <div key={g.key} className="flex items-center gap-2">
                    <span className="size-2.5 shrink-0 rounded-sm" style={{ background: g.color }} />
                    <span className="text-muted-foreground">{g.label}</span>
                    <span className="ml-auto font-medium tabular-nums">{formatCurrency(row[g.key])}</span>
                  </div>
                ))}
                <div className="flex items-center gap-2 border-t pt-1.5">
                  <span className="w-2.5" />
                  <span className="text-muted-foreground">Total</span>
                  <span className="ml-auto font-medium tabular-nums">{formatCurrency(row.total)}</span>
                </div>
              </div>
            )
          }} />
        <ChartLegend content={<ChartLegendContent />} itemSorter={(item) => GROUPS.findIndex((g) => g.key === item.dataKey)} />
        {GROUPS.map((g, i) => (
          <Bar key={g.key} dataKey={g.key} stackId="spend" fill={`var(--color-${g.key})`} stroke="var(--card)" strokeWidth={1}
            radius={i === GROUPS.length - 1 ? [4, 4, 0, 0] : 0} maxBarSize={36} />
        ))}
      </BarChart>
    </ChartContainer>
  )
}

const consumptionConfig = { kmPerLiter: { label: 'km/l', color: 'var(--data)' } } satisfies ChartConfig

/** Consumo de cada trecho entre tanques cheios. */
export function ConsumptionChart({ points }: { points: VehicleStats['consumption'] }) {
  const data = points.map((p) => ({ ...p, label: formatDate(p.date).slice(0, 5) }))
  return (
    <ChartContainer config={consumptionConfig} className="aspect-auto h-56 w-full">
      <LineChart data={data} margin={{ top: 8, left: 4, right: 12 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={16} />
        <YAxis tickLine={false} axisLine={false} width={40} domain={['auto', 'auto']} tickFormatter={(v: number) => formatNumber(v)} />
        <ChartTooltip
          content={(props) => {
            const { active, payload } = props as TooltipProps
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <div className="grid min-w-40 gap-1 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
                <p className="font-medium">{formatDate(row.date)}</p>
                <p><span className="font-medium tabular-nums">{formatNumber(row.kmPerLiter)} km/l</span></p>
                <p className="text-muted-foreground">{formatKm(row.km)} com {formatNumber(row.liters)} L</p>
              </div>
            )
          }} />
        <Line dataKey="kmPerLiter" type="monotone" stroke="var(--color-kmPerLiter)" strokeWidth={2}
          dot={{ r: 4, strokeWidth: 2, fill: 'var(--card)' }} activeDot={{ r: 6 }} />
      </LineChart>
    </ChartContainer>
  )
}
