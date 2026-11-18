import { CartesianGrid, Line, LineChart, ReferenceLine, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatDate } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import type { HistoryPoint } from '../types'

const config = { price: { label: 'Menor preço', color: 'var(--data)' } } satisfies ChartConfig

/** Menor preço de cada dia (qualquer loja), com o preço-alvo tracejado. */
export function PriceChart({ history, target }: { history: HistoryPoint[]; target: number | null }) {
  const { formatCompactCurrency, formatCurrency } = useMoney()
  const data = history.map((p) => ({ ...p, label: formatDate(p.day).slice(0, 5) }))
  return (
    <ChartContainer config={config} className="aspect-auto h-60 w-full">
      <LineChart data={data} margin={{ top: 12, left: 4, right: 12 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={24} />
        <YAxis tickLine={false} axisLine={false} width={64} domain={['auto', 'auto']}
          tickFormatter={(v: number) => formatCompactCurrency(v)} />
        {target && <ReferenceLine y={target} stroke="var(--muted-foreground)" strokeDasharray="4 4"
          label={{ value: 'Alvo', position: 'insideTopRight', fill: 'var(--muted-foreground)', fontSize: 11 }} />}
        <ChartTooltip
          cursor={{ strokeDasharray: '3 3' }}
          content={(props) => {
            const { active, payload } = props as { active?: boolean; payload?: ReadonlyArray<{ payload?: unknown }> }
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <div className="grid min-w-40 gap-1 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
                <p className="font-medium">{formatDate(row.day)}</p>
                <p className="flex justify-between gap-3"><span className="text-muted-foreground">{row.store}</span>
                  <span className="font-medium tabular-nums">{formatCurrency(row.price)}</span></p>
              </div>
            )
          }}
        />
        <Line dataKey="price" type="linear" stroke="var(--color-price)" strokeWidth={2} dot={data.length < 3}
          activeDot={{ r: 4 }} isAnimationActive={false} />
      </LineChart>
    </ChartContainer>
  )
}
