import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, XAxis, YAxis } from 'recharts'
import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { formatMonth, type YearMonth } from '@/lib/format'
import { useMoney } from '@/lib/hide-values'
import { STATUS } from '../labels'
import type { MonthTotal } from '../types'

const config = { total: { label: 'Fatura', color: 'var(--data)' } } satisfies ChartConfig

type Props = {
  months: MonthTotal[]
  /** Fatura mostrada na tela: barra cheia; as outras ficam mais claras. */
  selected: YearMonth
  budget: number | null
  onSelect: (month: YearMonth) => void
}

/** Faturas anteriores, a mostrada e as próximas (parcelas e fixos já lançados), com a meta tracejada. */
export function InvoiceChart({ months, selected, budget, onSelect }: Props) {
  const { formatCompactCurrency, formatCurrency } = useMoney()
  const data = months.map((m) => ({ ...m, total: Math.max(0, m.total), label: formatMonth(m.month, 'short') }))
  return (
    <ChartContainer config={config} className="aspect-auto h-56 w-full">
      <BarChart data={data} margin={{ top: 12, left: 4, right: 4 }} barCategoryGap="18%"
        onClick={(state) => {
          const index = Number((state as { activeTooltipIndex?: number | string } | null)?.activeTooltipIndex)
          if (Number.isInteger(index) && data[index]) onSelect(data[index].month)
        }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} interval="preserveStartEnd" />
        <YAxis tickLine={false} axisLine={false} width={60} tickFormatter={(v: number) => formatCompactCurrency(v)} />
        {budget && <ReferenceLine y={budget} stroke="var(--muted-foreground)" strokeDasharray="4 4"
          label={{ value: 'Meta', position: 'insideTopRight', fill: 'var(--muted-foreground)', fontSize: 11 }} />}
        <ChartTooltip
          cursor={{ fillOpacity: 0.3 }}
          content={(props) => {
            const { active, payload } = props as { active?: boolean; payload?: ReadonlyArray<{ payload?: unknown }> }
            if (!active || !payload?.length) return null
            const row = payload[0].payload as (typeof data)[number]
            return (
              <div className="grid min-w-40 gap-1 rounded-lg border bg-background px-2.5 py-2 text-xs shadow-xl">
                <p className="font-medium">{formatMonth(row.month)}</p>
                <p className="flex justify-between gap-3"><span className="text-muted-foreground">{STATUS[row.status].label}</span>
                  <span className="font-medium tabular-nums">{formatCurrency(row.total)}</span></p>
              </div>
            )
          }}
        />
        <Bar dataKey="total" radius={[4, 4, 0, 0]} className="cursor-pointer">
          {data.map((row) => (
            <Cell key={row.month} fill="var(--color-total)" fillOpacity={row.month === selected ? 1 : row.month > selected ? 0.3 : 0.55} />
          ))}
        </Bar>
      </BarChart>
    </ChartContainer>
  )
}
